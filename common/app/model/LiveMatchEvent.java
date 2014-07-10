package model;


import com.mongodb.WriteConcern;
import model.opta.OptaEvent;
import org.bson.types.ObjectId;
import org.jongo.marshall.jackson.oid.Id;
import play.Logger;
import utils.ListUtils;

import java.util.Date;
import java.util.List;


public class LiveMatchEvent {
    @Id
    public ObjectId liveMatchEventId;

    public ObjectId templateMatchEventId;
    public String optaMatchEventId;

    public SoccerTeam soccerTeamA;
    public SoccerTeam soccerTeamB;

    public Date startDate;
    public Date createdAt;

    public LiveMatchEvent() { }

    public LiveMatchEvent(TemplateMatchEvent templateMatchEvent) {
        templateMatchEventId = templateMatchEvent.templateMatchEventId;
        optaMatchEventId = templateMatchEvent.optaMatchEventId;
        soccerTeamA = templateMatchEvent.soccerTeamA;
        soccerTeamB = templateMatchEvent.soccerTeamB;
        startDate = templateMatchEvent.startDate;
        createdAt = GlobalDate.getCurrentDate();
    }

    static public LiveMatchEvent create(TemplateMatchEvent templateMatchEvent) {
        // Creamos la version "live" del template Match Event
        LiveMatchEvent liveMatchEvent = new LiveMatchEvent(templateMatchEvent);

        // Generamos el objectId para poder devolverlo correctamente
        liveMatchEvent.liveMatchEventId = new ObjectId();
        Model.liveMatchEvents().withWriteConcern(WriteConcern.SAFE).insert(liveMatchEvent);

        return liveMatchEvent;
    }

    public SoccerPlayer findSoccerPlayer(ObjectId templateSoccerPlayerId) {
        for(SoccerPlayer soccer : soccerTeamA.soccerPlayers) {
            if(soccer.templateSoccerPlayerId.compareTo(templateSoccerPlayerId) == 0)
                return soccer;
        }

        for(SoccerPlayer soccer : soccerTeamB.soccerPlayers) {
            if(soccer.templateSoccerPlayerId.compareTo(templateSoccerPlayerId) == 0)
                return soccer;
        }

        return null;
    }

    /**
     * Buscamos el "live" a partir de su "template"
     */
    static public LiveMatchEvent find(TemplateMatchEvent templateMatchEvent) {
        return Model.liveMatchEvents().findOne("{templateMatchEventId: #}", templateMatchEvent.templateMatchEventId).as(LiveMatchEvent.class);
    }

    static public LiveMatchEvent find(ObjectId liveMatchEventId) {
        return Model.liveMatchEvents().findOne("{_id : #}", liveMatchEventId).as(LiveMatchEvent.class);
    }

    public static Iterable<LiveMatchEvent> find(String fieldId, List<ObjectId> idList) {
        return Model.findObjectIds(Model.liveMatchEvents(), fieldId, idList).as(LiveMatchEvent.class);
    }


    /**
     * Calcular y actualizar los puntos fantasy de un determinado partido "live"
     * Opera sobre cada uno de los futbolistas del partido (teamA y teamB)
     */
    static public void updateLiveFantasyPoints(LiveMatchEvent liveMatchEvent) {
        Logger.info("update Live: {} vs {} ({})", liveMatchEvent.soccerTeamA.name, liveMatchEvent.soccerTeamB.name, liveMatchEvent.startDate);

        // Actualizamos los jugadores del TeamA
        for (SoccerPlayer soccer : liveMatchEvent.soccerTeamA.soccerPlayers) {
            updateLiveFantasyPoints(liveMatchEvent.optaMatchEventId, soccer);
        }

        // Actualizamos los jugadores del TeamB
        for (SoccerPlayer soccer : liveMatchEvent.soccerTeamB.soccerPlayers) {
            updateLiveFantasyPoints(liveMatchEvent.optaMatchEventId, soccer);
        }
    }

    /**
     * Calcular y actualizar los puntos fantasy de un determinado futbolista en los partidos "live"
     *
     * @param optaMatchId  Partido que se ha jugado
     */
    static private void updateLiveFantasyPoints(String optaMatchId, SoccerPlayer soccerPlayer) {
        // Logger.info("search points: {}: optaId({})", soccerPlayer.name, soccerPlayer.optaPlayerId);

        // TODO: Quitamos el primer caracter ("p": player / "g": "match")
        String playerId = Model.getPlayerIdFromOpta(soccerPlayer.optaPlayerId);
        String matchId = Model.getMatchEventIdFromOpta(optaMatchId);

        //TODO: ¿ $sum (aggregation) ?
        // Obtener los puntos fantasy obtenidos por el futbolista en un partido
        Iterable<OptaEvent> optaEventResults = Model.optaEvents().find("{optaPlayerId: #, gameId: #}",
                playerId, matchId).as(OptaEvent.class);

        // Sumarlos
        int points = 0;
        for (OptaEvent point: optaEventResults) {
            points += point.points;
        }
        /*
        if (points > 0) {
            Logger.info("--> {}: {} = {}", soccerPlayer.optaPlayerId, soccerPlayer.name, points);
        }
        */

        // filter().aggregate("{$match: {optaPlayerId: #}}", soccerPlayer.optaPlayerId);

        // Actualizar sus puntos en cada LiverMatchEvent en el que participe
        setLiveFantasyPointsOfSoccerPlayer(optaMatchId, soccerPlayer.templateSoccerPlayerId, points);
    }

    /**
     * Actualizar los puntos fantasy de un determinado futbolista en los partidos "live"
     */
    static private void setLiveFantasyPointsOfSoccerPlayer(String optaMatchId, ObjectId soccerPlayerId, int points) {
        //Logger.info("setLiveFantasyPoints: {} = {} fantasy points", soccerPlayerId, strPoints);

        long startTime = System.currentTimeMillis();

        // TODO: Pasar el equipo del futbolista para simplificar la query
        // Actualizar jugador si aparece en TeamA
        Model.liveMatchEvents()
                .update("{optaMatchEventId: #, soccerTeamA.soccerPlayers.templateSoccerPlayerId: #}", optaMatchId, soccerPlayerId)
                .multi()
                .with("{$set: {soccerTeamA.soccerPlayers.$.fantasyPoints: #}}", points);

        // Actualizar jugador si aparece en TeamB
        Model.liveMatchEvents()
                .update("{optaMatchEventId: #, soccerTeamB.soccerPlayers.templateSoccerPlayerId: #}", optaMatchId, soccerPlayerId)
                .multi()
                .with("{$set: {soccerTeamB.soccerPlayers.$.fantasyPoints: #}}", points);

        //Logger.info("END: setLiveFantasyPoints: {}", System.currentTimeMillis() - startTime);
    }


    /**
     * Buscar el tiempo actual del partido
     *
     * @return TODO: Tiempo transcurrido
     */
    public static Date currentTime(String liveMatchEventId) {
        LiveMatchEvent liveMatchEvent = find(new ObjectId(liveMatchEventId));
        Date dateNow = liveMatchEvent.startDate;

        String optaId = Model.getMatchEventIdFromOpta(liveMatchEvent.optaMatchEventId);

        // Buscar el ultimo evento registrado por el partido
        Iterable<OptaEvent> optaEvents = Model.optaEvents().find("{gameId: #}", optaId).sort("{timestamp: -1}").limit(1).as(OptaEvent.class);
        if (optaEvents.iterator().hasNext()) {
            OptaEvent event = optaEvents.iterator().next();
            dateNow = event.timestamp;
            Logger.info("currentTime from optaEvent: gameId({}) id({})", optaId, event.eventId);
        }

        Logger.info("currentTime ({}): {}", liveMatchEvent.optaMatchEventId, dateNow);
        return dateNow;
    }
}
