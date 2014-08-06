package model;

import com.fasterxml.jackson.annotation.JsonView;
import model.opta.OptaEvent;
import model.opta.OptaMatchEvent;
import org.bson.types.ObjectId;
import org.jongo.marshall.jackson.oid.Id;
import play.Logger;
import utils.ListUtils;

import java.util.*;

public class MatchEvent {
    public enum PeriodType {
        PRE_GAME(0),
        FIRST_HALF(1),
        SECOND_HALF(2),
        POST_GAME(3);

        public final int id;

        PeriodType(int id) {
            this.id = id;
        }
    }

    @Id
    public ObjectId matchEventId;

    public ObjectId templateMatchEventId;

    public String optaMatchEventId;
    public String optaCompetitionId;
    public String optaSeasonId;

    public SoccerTeam soccerTeamA;
    public SoccerTeam soccerTeamB;

    // Asocia un soccerPlayerId con fantasyPoints
    @JsonView(JsonViews.FullContest.class)
    public HashMap<String, Integer> livePlayerToPoints = new HashMap<>();

    public PeriodType period = PeriodType.PRE_GAME;

    public Date startDate;
    public Date createdAt;

    public MatchEvent() { }

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

    static public MatchEvent findOne(ObjectId matchEventId) {
        return Model.matchEvents().findOne("{_id : #}", matchEventId).as(MatchEvent.class);
    }

    static public MatchEvent findOneFromOptaId(String optaMatchEventId) {
        return Model.matchEvents().findOne("{optaMatchEventId: #}", optaMatchEventId).as(MatchEvent.class);
    }

    static public MatchEvent findOneFromTemplate(ObjectId templateMatchEventId) {
        return Model.matchEvents().findOne("{templateMatchEventId : #}", templateMatchEventId).as(MatchEvent.class);
    }

    public static List<MatchEvent> findAll() {
        return ListUtils.asList(Model.matchEvents().find().as(MatchEvent.class));
    }

    public static List<MatchEvent> findAllFromTemplate(List<ObjectId> idList) {
        return ListUtils.asList(Model.findObjectIds(Model.matchEvents(), "templateMatchEventId", idList).as(MatchEvent.class));
    }

    static public List<MatchEvent> gatherFromTemplateContests(Iterable<TemplateContest> templateContests) {
        List<ObjectId> templateMatchEventObjectIds = new ArrayList<>();

        for (TemplateContest templateContest: templateContests) {
            templateMatchEventObjectIds.addAll(templateContest.templateMatchEventIds);
        }

        return ListUtils.asList(Model.findObjectIds(Model.matchEvents(), "templateMatchEventId", templateMatchEventObjectIds).as(MatchEvent.class));
    }

    /**
     *  Estado del partido
     */
    public boolean isStarted() {
        return OptaEvent.isGameStarted(optaMatchEventId);
    }

    public boolean isFinished() {
        return OptaEvent.isGameFinished(optaMatchEventId);
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

    public HashMap<String, SoccerTeam> getSoccerTeamsAsMap(){
        HashMap<String, SoccerTeam> map = new HashMap<>();
        map.put(soccerTeamA.optaTeamId, soccerTeamA);
        map.put(soccerTeamB.optaTeamId, soccerTeamB);
        return map;
    }

    public HashMap<String, SoccerPlayer> getSoccerPlayersAsMap(){
        HashMap<String, SoccerPlayer> map = new HashMap<>();
        for (SoccerPlayer soccerPlayer : soccerTeamA.soccerPlayers) {
            map.put(soccerPlayer.optaPlayerId, soccerPlayer);
        }
        for (SoccerPlayer soccerPlayer : soccerTeamB.soccerPlayers) {
            map.put(soccerPlayer.optaPlayerId, soccerPlayer);
        }
        return map;
    }

    public void saveStats() {
        saveStats(soccerTeamA);
        saveStats(soccerTeamB);
    }

    public void saveStats(SoccerTeam soccerTeam) {
        for (SoccerPlayer soccerPlayer : soccerTeam.soccerPlayers) {
            // Buscamos el template
            TemplateSoccerPlayer templateSoccerPlayer = TemplateSoccerPlayer.findOne(soccerPlayer.templateSoccerPlayerId);

            // Eliminamos las estadísticas del partido que hubieramos registrado anteriormente
            for (SoccerPlayerStats stats : templateSoccerPlayer.stats) {
                if (stats.optaMatchEventId.equals(optaMatchEventId)) {
                    templateSoccerPlayer.stats.remove(stats);
                    // Logger.debug("------> OptaMatchEventId({}): Fecha({}): stats modificadas !!!", optaMatchEventId, GlobalDate.getCurrentDate().toString());
                    break;
                }
            }

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

    static public void createFromTemplate(TemplateMatchEvent templateMatchEvent) {
        TemplateSoccerTeam teamA = Model.templateSoccerTeams().findOne("{_id: #}", templateMatchEvent.templateSoccerTeamAId).as(TemplateSoccerTeam.class);
        TemplateSoccerTeam teamB = Model.templateSoccerTeams().findOne("{_id: #}", templateMatchEvent.templateSoccerTeamBId).as(TemplateSoccerTeam.class);
        assert(teamA != null && teamB != null);

        MatchEvent matchEvent = new MatchEvent();
        matchEvent.startDate = templateMatchEvent.startDate;
        matchEvent.optaMatchEventId = templateMatchEvent.optaMatchEventId;
        matchEvent.optaCompetitionId = templateMatchEvent.optaCompetitionId;
        matchEvent.optaSeasonId = templateMatchEvent.optaSeasonId;
        matchEvent.soccerTeamA = SoccerTeam.create(matchEvent, teamA);
        matchEvent.soccerTeamB = SoccerTeam.create(matchEvent, teamB);
        matchEvent.createdAt = GlobalDate.getCurrentDate();

        matchEvent.insertLivePlayersFromTeam(matchEvent.soccerTeamA);
        matchEvent.insertLivePlayersFromTeam(matchEvent.soccerTeamB);

        Model.matchEvents().update("{templateMatchEventId: #}", templateMatchEvent.templateMatchEventId).upsert().with(matchEvent);
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

    public void updateState() {
        if (period == null) {
            period = PeriodType.PRE_GAME;
        }

        PeriodType periodBackup = period;

        if (period == PeriodType.PRE_GAME) {
            // Primera Parte?
            if (Model.optaEvents().findOne("{gameId: #, periodId: 1}", optaMatchEventId).as(OptaEvent.class) != null) {
                period = PeriodType.FIRST_HALF;
            }
        }

        if (period == PeriodType.FIRST_HALF) {
            // Segunda Parte?
            if (Model.optaEvents().findOne("{gameId: #, periodId: 2}", optaMatchEventId).as(OptaEvent.class) != null) {
                period = PeriodType.SECOND_HALF;
            }
        }

        if (period == PeriodType.SECOND_HALF) {
            // Segunda Parte?
            if (Model.optaEvents().findOne("{gameId: #, periodId: 14}", optaMatchEventId).as(OptaEvent.class) != null) {
                period = PeriodType.POST_GAME;
            }
        }

        if (!period.equals(periodBackup)) {
            Model.matchEvents().update("{_id: #}", matchEventId).with("{$set: {period: #}}", period);
        }
    }

    /**
     * Calcular y actualizar los puntos fantasy de un determinado partido "live"
     * Opera sobre cada uno de los futbolistas del partido (teamA y teamB)
     */
    public void updateFantasyPoints() {
        Logger.info("update Live: matchEvent: {}", templateMatchEventId.toString());

        for (SoccerPlayer soccerPlayer : soccerTeamA.soccerPlayers) {
            updateFantasyPoints(soccerPlayer);
        }

        for (SoccerPlayer soccerPlayer : soccerTeamB.soccerPlayers) {
            updateFantasyPoints(soccerPlayer);
        }
    }

    /**
     * Calcular y actualizar los puntos fantasy de un determinado futbolista en los partidos "live"
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
        Model.matchEvents()
                .update(searchPattern, optaMatchId)
                .multi()
                .with(setPattern, points);
    }

    /**
     * Buscar el tiempo actual del partido
     *
     * @return TODO: Tiempo transcurrido
     */
    public static Date getLastEventDate(String matchEventId) {
        MatchEvent matchEvent = findOne(new ObjectId(matchEventId));
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
