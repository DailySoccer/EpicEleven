package model;


import com.fasterxml.jackson.annotation.JsonView;
import model.opta.OptaEvent;
import model.opta.OptaMatchEvent;
import org.bson.types.ObjectId;
import org.jongo.Find;
import org.jongo.marshall.jackson.oid.Id;
import play.Logger;
import utils.ListUtils;

import java.util.*;

public class TemplateMatchEvent implements JongoId, Initializer {
    @Id
    public ObjectId templateMatchEventId;

    public String optaMatchEventId;
    public String optaCompetitionId;
    public String optaSeasonId;

    public SoccerTeam soccerTeamA;
    public SoccerTeam soccerTeamB;

    // Asocia un soccerPlayerId con fantasyPoints
    @JsonView(JsonViews.Live.class)
    public HashMap<String, Integer> livePlayerToPoints = new HashMap<>();

    public Date startDate;
    public Date createdAt;

    public TemplateMatchEvent() { }

    public void Initialize() { }

    public ObjectId getId() {
        return templateMatchEventId;
    }

    public boolean hasChanged(OptaMatchEvent optaMatchEvent) {
        return !optaMatchEventId.equals(optaMatchEvent.optaMatchEventId) ||
               !soccerTeamA.optaTeamId.equals(optaMatchEvent.homeTeamId) ||
               !soccerTeamB.optaTeamId.equals(optaMatchEvent.awayTeamId) ||
               !startDate.equals(optaMatchEvent.matchDate);
    }

    static public TemplateMatchEvent findOne(ObjectId templateMatchEventId) {
        return Model.templateMatchEvents().findOne("{_id : #}", templateMatchEventId).as(TemplateMatchEvent.class);
    }

    static public TemplateMatchEvent findOneFromOptaId(String optaMatchEventId) {
        return Model.templateMatchEvents().findOne("{optaMatchEventId: #}", optaMatchEventId).as(TemplateMatchEvent.class);
    }

    public static List<TemplateMatchEvent> findAll() {
        return ListUtils.asList(Model.templateMatchEvents().find().as(TemplateMatchEvent.class));
    }

    public static List<TemplateMatchEvent> findAll(List<ObjectId> idList) {
        return ListUtils.asList(Model.findObjectIds(Model.templateMatchEvents(), "_id", idList).as(TemplateMatchEvent.class));
    }

    static public List<TemplateMatchEvent> gatherFromTemplateContests(Iterable<TemplateContest> templateContests) {
        List<ObjectId> templateMatchEventObjectIds = new ArrayList<>();

        for (TemplateContest templateContest: templateContests) {
            templateMatchEventObjectIds.addAll(templateContest.templateMatchEventIds);
        }

        return ListUtils.asList(Model.findObjectIds(Model.templateMatchEvents(), "_id", templateMatchEventObjectIds).as(TemplateMatchEvent.class));
    }

    /**
     *  Estado del partido
     */
    public boolean isStarted() {
        // Un partido "comenzará" x minutos antes de su fecha establecida de comienzo
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(startDate);
        calendar.add(Calendar.MINUTE, -5);
        return GlobalDate.getCurrentDate().after(calendar.getTime());

         /*
        OptaEvent optaEvent = Model.optaEvents().findOne("{gameId: #, typeId: 32, periodId: 1}", optaMatchEventId).as(OptaEvent.class);
        if (optaEvent == null) {
            // Kick Off Pass?
            optaEvent = Model.optaEvents().findOne("{gameId: #, typeId: 1, periodId: 1, qualifiers: 278}", optaMatchEventId).as(OptaEvent.class);
        }
        */

        /*
        Logger.info("isStarted? {}({}) = {}",
                find.soccerTeamA.name + " vs " + find.soccerTeamB.name, find.optaMatchEventId, (optaEvent!= null));
        */
    }

    public static boolean isStarted(String templateMatchEventId) {
        TemplateMatchEvent templateMatch = findOne(new ObjectId(templateMatchEventId));
        return templateMatch.isStarted();
    }

    public boolean isFinished() {
        OptaEvent optaEvent = Model.optaEvents().findOne("{gameId: #, typeId: 30, periodId: 14}", optaMatchEventId).as(OptaEvent.class);

        /*
        Logger.info("isFinished? {}({}) = {}",
                find.soccerTeamA.name + " vs " + find.soccerTeamB.name, find.optaMatchEventId, (optaEvent!= null));
        */
        return (optaEvent != null);
    }

    public static boolean isFinished(String templateMatchEventId) {
        TemplateMatchEvent templateMatch = findOne(new ObjectId(templateMatchEventId));
        return templateMatch.isFinished();
    }

    public int getFantasyPoints(SoccerTeam soccerTeam) {
        int points = 0;
        for (SoccerPlayer soccerPlayer : soccerTeam.soccerPlayers) {
            points += getFantasyPoints(soccerPlayer.templateSoccerPlayerId);
        }
        return points;
    }

    public int getFantasyPoints(ObjectId soccerPlayerId) {
        return livePlayerToPoints.get(soccerPlayerId.toString());
    }

    public void saveStats() {
        saveStats(soccerTeamA);
        saveStats(soccerTeamB);
    }

    public void saveStats(SoccerTeam soccerTeam) {
        for (SoccerPlayer soccerPlayer : soccerTeam.soccerPlayers) {
            // Buscamos el template
            TemplateSoccerPlayer templateSoccerPlayer = TemplateSoccerPlayer.findOne(soccerPlayer.templateSoccerPlayerId);

            // Generamos las nuevas estadísticas del partido
            SoccerPlayerStats soccerPlayerStats = new SoccerPlayerStats(soccerPlayer.optaPlayerId, optaMatchEventId, getFantasyPoints(soccerPlayer.templateSoccerPlayerId));
            soccerPlayerStats.updateStats();

            // El futbolista ha jugado en el partido?
            if (soccerPlayerStats.playedMinutes > 0 && !soccerPlayerStats.events.isEmpty()) {

                templateSoccerPlayer.stats.add(soccerPlayerStats);

                // Calculamos la media de los fantasyPoints
                int fantasyPointsMedia = 0;
                for (SoccerPlayerStats stats : templateSoccerPlayer.stats) {
                    fantasyPointsMedia += stats.fantasyPoints;
                }
                fantasyPointsMedia /= templateSoccerPlayer.stats.size();

                // Grabar cambios
                Model.templateSoccerPlayers().update("{optaPlayerId: #}", soccerPlayerStats.optaPlayerId)
                        .with("{$set: {fantasyPoints: #, stats: #}}", fantasyPointsMedia, templateSoccerPlayer.stats);

                /*
                Logger.debug("saveStats: {}({}) - minutes = {} - points = {} - events({})",
                        soccerPlayer.name, soccerPlayer.optaPlayerId, soccerPlayerStats.playedMinutes, getFantasyPoints(soccerPlayer.templateSoccerPlayerId), soccerPlayerStats.events);
                */
            }
        }
    }

    static public boolean importMatchEvent(OptaMatchEvent optaMatchEvent) {
        TemplateSoccerTeam teamA = Model.templateSoccerTeams().findOne("{optaTeamId: #}", optaMatchEvent.homeTeamId).as(TemplateSoccerTeam.class);
        TemplateSoccerTeam teamB = Model.templateSoccerTeams().findOne("{optaTeamId: #}", optaMatchEvent.awayTeamId).as(TemplateSoccerTeam.class);

        if (teamA != null && teamB != null) {
            create(optaMatchEvent, teamA, teamB, optaMatchEvent.matchDate);

            Model.optaMatchEvents().update("{id: #}", optaMatchEvent.optaMatchEventId).with("{$set: {dirty: false}}");
        }
        else {
            Logger.error("Ignorando OptaMatchEvent: {} ({})", optaMatchEvent.optaMatchEventId, optaMatchEvent.matchDate);
            return false;
        }
        return true;
    }

    static private TemplateMatchEvent create(OptaMatchEvent optaMatchEvent, TemplateSoccerTeam teamA, TemplateSoccerTeam teamB, Date startDate) {
        Logger.info("Template MatchEvent: {} vs {} ({})", teamA.name, teamB.name, startDate);

        TemplateMatchEvent templateMatchEvent = new TemplateMatchEvent();
        templateMatchEvent.startDate = startDate;
        templateMatchEvent.optaMatchEventId = optaMatchEvent.optaMatchEventId;
        templateMatchEvent.optaCompetitionId = optaMatchEvent.competitionId;
        templateMatchEvent.optaSeasonId = optaMatchEvent.seasonId;
        templateMatchEvent.soccerTeamA = SoccerTeam.create(templateMatchEvent, teamA);
        templateMatchEvent.soccerTeamB = SoccerTeam.create(templateMatchEvent, teamB);
        templateMatchEvent.createdAt = GlobalDate.getCurrentDate();

        templateMatchEvent.insertLivePlayersFromTeam(templateMatchEvent.soccerTeamA);
        templateMatchEvent.insertLivePlayersFromTeam(templateMatchEvent.soccerTeamB);

        Model.templateMatchEvents().update("{optaMatchEventId: #}", optaMatchEvent.optaMatchEventId).upsert().with(templateMatchEvent);

        return templateMatchEvent;
    }

    static public boolean isInvalid(OptaMatchEvent optaMatchEvent) {
        boolean invalid = (optaMatchEvent.homeTeamId == null) || optaMatchEvent.homeTeamId.isEmpty() ||
                          (optaMatchEvent.awayTeamId == null) || optaMatchEvent.awayTeamId.isEmpty();

        if (!invalid) {
            TemplateSoccerTeam teamA = Model.templateSoccerTeams().findOne("{optaTeamId: #}", optaMatchEvent.homeTeamId).as(TemplateSoccerTeam.class);
            TemplateSoccerTeam teamB = Model.templateSoccerTeams().findOne("{optaTeamId: #}", optaMatchEvent.awayTeamId).as(TemplateSoccerTeam.class);
            invalid = (teamA == null) || (teamB == null);
        }

        return invalid;
    }

    /**
     * Calcular y actualizar los puntos fantasy de un determinado partido "live"
     * Opera sobre cada uno de los futbolistas del partido (teamA y teamB)
     */
    public void updateFantasyPoints() {
        Logger.info("update Live: templateMatchEvent: {}", templateMatchEventId.toString());

        for (SoccerPlayer soccerPlayer : soccerTeamA.soccerPlayers) {
            updateFantasyPoints(soccerPlayer);
        }

        for (SoccerPlayer soccerPlayer : soccerTeamB.soccerPlayers) {
            updateFantasyPoints(soccerPlayer);
        }
    }

    /**
     * Calcular y actualizar los puntos fantasy de un determinado futbolista en los partidos "live"
     *
     * TODO: ¿ $sum (aggregation) ?
     */
    private void updateFantasyPoints(SoccerPlayer soccerPlayer) {
        // Obtener los puntos fantasy obtenidos por el futbolista en un partido
        Iterable<OptaEvent> optaEventResults = Model.optaEvents().find("{optaPlayerId: #, gameId: #}",
                soccerPlayer.optaPlayerId, optaMatchEventId).as(OptaEvent.class);

        // Sumarlos
        int points = 0;
        for (OptaEvent point: optaEventResults) {
            points += point.points;
        }
        // if (points > 0) Logger.info("--> {}: {} = {}", soccerPlayer.optaPlayerId, soccerPlayer.name, points);

        // Actualizar sus puntos en cada LiverMatchEvent en el que participe
        setLiveFantasyPointsOfSoccerPlayer(optaMatchEventId, soccerPlayer.templateSoccerPlayerId.toString(), points);
    }

    /**
     * Actualizar los puntos fantasy de un determinado futbolista en los partidos "live"
     */
    static private void setLiveFantasyPointsOfSoccerPlayer(String optaMatchId, String soccerPlayerId, int points) {
        //Logger.info("setLiveFantasyPoints: {} = {} fantasy points", soccerPlayerId, points);

        String searchPattern = String.format("{optaMatchEventId: #, 'livePlayerToPoints.%s': {$exists: 1}}", soccerPlayerId);
        String setPattern = String.format("{$set: {'livePlayerToPoints.%s': #}}", soccerPlayerId);
        Model.templateMatchEvents()
                .update(searchPattern, optaMatchId)
                .multi()
                .with(setPattern, points);
    }

    /**
     * Buscar el tiempo actual del partido
     *
     * @return TODO: Tiempo transcurrido
     */
    public static Date currentTime(String templateMatchEventId) {
        TemplateMatchEvent matchEvent = findOne(new ObjectId(templateMatchEventId));
        Date dateNow = matchEvent.startDate;

        // Buscar el ultimo evento registrado por el partido
        Iterable<OptaEvent> optaEvents = Model.optaEvents().find("{gameId: #}", matchEvent.optaMatchEventId).sort("{timestamp: -1}").limit(1).as(OptaEvent.class);
        if (optaEvents.iterator().hasNext()) {
            OptaEvent event = optaEvents.iterator().next();
            dateNow = event.timestamp;
            Logger.info("currentTime from optaEvent: gameId({}) id({})", matchEvent.optaMatchEventId, event.eventId);
        }

        Logger.info("currentTime ({}): {}", matchEvent.optaMatchEventId, dateNow);
        return dateNow;
    }

    private void insertLivePlayersFromTeam(SoccerTeam soccerTeam) {
        for (SoccerPlayer soccerPlayer : soccerTeam.soccerPlayers) {
            livePlayerToPoints.put(soccerPlayer.templateSoccerPlayerId.toString(), 0);
        }
    }
}
