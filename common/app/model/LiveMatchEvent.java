package model;


import com.fasterxml.jackson.annotation.JsonView;
import com.mongodb.WriteConcern;
import model.opta.OptaEvent;
import org.bson.types.ObjectId;
import org.jongo.marshall.jackson.oid.Id;
import play.Logger;
import utils.ListUtils;

import java.util.Date;
import java.util.HashMap;
import java.util.List;


public class LiveMatchEvent {
    @Id
    public ObjectId liveMatchEventId;

    public ObjectId templateMatchEventId;

    @JsonView(JsonViews.NotForClient.class)
    public String optaMatchEventId;

    // Asocia un soccerPlayerId con fantasyPoints
    public HashMap<String, Integer> soccerPlayerToPoints = new HashMap<>();

    @JsonView(JsonViews.NotForClient.class)
    public Date startDate;

    @JsonView(JsonViews.NotForClient.class)
    public Date createdAt;

    // Asocia un soccerPlayerId con optaPlayerId (el id de Opta se necesita para las querys a optaEvents)
    @JsonView(JsonViews.NotForClient.class)
    public HashMap<String, String> soccerPlayerToOpta = new HashMap<>();

    public LiveMatchEvent() { }

    public LiveMatchEvent(TemplateMatchEvent templateMatchEvent) {
        templateMatchEventId = templateMatchEvent.templateMatchEventId;
        optaMatchEventId = templateMatchEvent.optaMatchEventId;

        insertSoccerPlayersFromTeam(templateMatchEvent.soccerTeamA);
        insertSoccerPlayersFromTeam(templateMatchEvent.soccerTeamB);

        createdAt = GlobalDate.getCurrentDate();
    }

    public TemplateMatchEvent getTemplateMatchEvent() {
        return TemplateMatchEvent.findOne(templateMatchEventId);
    }

    public int getFantasyPoints(SoccerTeam soccerTeam) {
        int points = 0;
        for (SoccerPlayer soccerPlayer : soccerTeam.soccerPlayers) {
            points += getFantasyPoints(soccerPlayer.templateSoccerPlayerId.toString());
        }
        return points;
    }

    public int getFantasyPoints(String soccerPlayerId) {
        return soccerPlayerToPoints.get(soccerPlayerId);
    }

    static public LiveMatchEvent create(TemplateMatchEvent templateMatchEvent) {
        // Creamos la version "live" del template Match Event
        LiveMatchEvent liveMatchEvent = new LiveMatchEvent(templateMatchEvent);

        Model.liveMatchEvents().withWriteConcern(WriteConcern.SAFE).insert(liveMatchEvent);
        assert(liveMatchEvent.liveMatchEventId != null);

        return liveMatchEvent;
    }

    static public LiveMatchEvent findOne(ObjectId liveMatchEventId) {
        return Model.liveMatchEvents().findOne("{_id : #}", liveMatchEventId).as(LiveMatchEvent.class);
    }

    static public List<LiveMatchEvent> findAllFromTemplateMatchEvents(List<ObjectId> idList) {
        return ListUtils.asList(Model.findObjectIds(Model.liveMatchEvents(), "templateMatchEventId", idList).as(LiveMatchEvent.class));
    }

    static public LiveMatchEvent findFromTemplateMatchEvent(TemplateMatchEvent templateMatchEvent) {
        return Model.liveMatchEvents().findOne("{templateMatchEventId: #}", templateMatchEvent.templateMatchEventId).as(LiveMatchEvent.class);
    }

    /**
     * Calcular y actualizar los puntos fantasy de un determinado partido "live"
     * Opera sobre cada uno de los futbolistas del partido (teamA y teamB)
     */
    public void updateFantasyPoints() {
        Logger.info("update Live: templateMatchEvent: {}", templateMatchEventId.toString());

        for (String soccerPlayerId : soccerPlayerToPoints.keySet()) {
            updateFantasyPoints(soccerPlayerId);
        }
    }

    /**
     * Calcular y actualizar los puntos fantasy de un determinado futbolista en los partidos "live"
     */
    private void updateFantasyPoints(String soccerPlayerId) {
         //TODO: ¿ $sum (aggregation) ?
        // Obtener los puntos fantasy obtenidos por el futbolista en un partido
        Iterable<OptaEvent> optaEventResults = Model.optaEvents().find("{optaPlayerId: #, gameId: #}",
                soccerPlayerToOpta.get(soccerPlayerId), optaMatchEventId).as(OptaEvent.class);

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
        setLiveFantasyPointsOfSoccerPlayer(optaMatchEventId, soccerPlayerId, points);
    }

    /**
     * Actualizar los puntos fantasy de un determinado futbolista en los partidos "live"
     */
    static private void setLiveFantasyPointsOfSoccerPlayer(String optaMatchId, String soccerPlayerId, int points) {
        //Logger.info("setLiveFantasyPoints: {} = {} fantasy points", soccerPlayerId, points);

        long startTime = System.currentTimeMillis();

        String searchPattern = String.format("{optaMatchEventId: #, 'soccerPlayerToPoints.%s': {$exists: 1}}", soccerPlayerId);
        String setPattern = String.format("{$set: {'soccerPlayerToPoints.%s': #}}", soccerPlayerId);
        Model.liveMatchEvents()
                .update(searchPattern, optaMatchId)
                .multi()
                .with(setPattern, points);

        //Logger.info("END: setLiveFantasyPoints: {}", System.currentTimeMillis() - startTime);
    }


    /**
     * Buscar el tiempo actual del partido
     *
     * @return TODO: Tiempo transcurrido
     */
    public static Date currentTime(String liveMatchEventId) {
        LiveMatchEvent liveMatchEvent = findOne(new ObjectId(liveMatchEventId));
        Date dateNow = liveMatchEvent.startDate;

        // Buscar el ultimo evento registrado por el partido
        Iterable<OptaEvent> optaEvents = Model.optaEvents().find("{gameId: #}", liveMatchEvent.optaMatchEventId).sort("{timestamp: -1}").limit(1).as(OptaEvent.class);
        if (optaEvents.iterator().hasNext()) {
            OptaEvent event = optaEvents.iterator().next();
            dateNow = event.timestamp;
            Logger.info("currentTime from optaEvent: gameId({}) id({})", liveMatchEvent.optaMatchEventId, event.eventId);
        }

        Logger.info("currentTime ({}): {}", liveMatchEvent.optaMatchEventId, dateNow);
        return dateNow;
    }

    private void insertSoccerPlayersFromTeam(SoccerTeam soccerTeam) {
        for (SoccerPlayer soccerPlayer : soccerTeam.soccerPlayers) {
            soccerPlayerToPoints.put(soccerPlayer.templateSoccerPlayerId.toString(), 0);
            soccerPlayerToOpta.put(soccerPlayer.templateSoccerPlayerId.toString(), soccerPlayer.optaPlayerId);
        }
    }
}
