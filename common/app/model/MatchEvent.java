package model;

import com.fasterxml.jackson.annotation.JsonView;
import model.opta.OptaEvent;
import model.opta.OptaEventType;
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
    public HashMap<String, LiveFantasyPoints> liveFantasyPoints = new HashMap<>();

    public PeriodType period = PeriodType.PRE_GAME;
    public int minutesPlayed;

    public Date startDate;
    public Date createdAt;

    public MatchEvent() { }

    public void Initialize() { }

    public ObjectId getId() {
        return templateMatchEventId;
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

    static public List<MatchEvent> gatherFromTemplateContests(List<TemplateContest> templateContests) {
        List<ObjectId> templateMatchEventObjectIds = new ArrayList<>(templateContests.size());

        for (TemplateContest templateContest: templateContests) {
            templateMatchEventObjectIds.addAll(templateContest.templateMatchEventIds);
        }

        return ListUtils.asList(Model.findObjectIds(Model.matchEvents(), "templateMatchEventId", templateMatchEventObjectIds).as(MatchEvent.class));
    }


    public boolean isStarted()  { return OptaEvent.isGameStarted(optaMatchEventId);  }
    public boolean isFinished() { return OptaEvent.isGameFinished(optaMatchEventId); }

    public int getFantasyPoints(SoccerTeam soccerTeam) {
        if (soccerTeam != soccerTeamA && soccerTeam != soccerTeamB)
            throw new RuntimeException("WTF 2771");

        int points = 0;
        for (SoccerPlayer soccerPlayer : soccerTeam.soccerPlayers) {
            points += getFantasyPoints(soccerPlayer.templateSoccerPlayerId);
        }
        return points;
    }

    public boolean containsSoccerPlayer(ObjectId soccerPlayerId) {
        return liveFantasyPoints.containsKey(soccerPlayerId.toString());
    }

    public int getFantasyPoints(ObjectId soccerPlayerId) {
        return liveFantasyPoints.get(soccerPlayerId.toString()).points;
    }

    public void saveStats() {
        saveStats(soccerTeamA);
        saveStats(soccerTeamB);
    }

    public void saveStats(SoccerTeam soccerTeam) {
        for (SoccerPlayer soccerPlayer : soccerTeam.soccerPlayers) {
            TemplateSoccerPlayer templateSoccerPlayer = TemplateSoccerPlayer.findOne(soccerPlayer.templateSoccerPlayerId);

            // Eliminamos las estadísticas del partido que hubieramos registrado anteriormente
            for (SoccerPlayerStats stats : templateSoccerPlayer.stats) {
                if (stats.optaMatchEventId.equals(optaMatchEventId)) {
                    templateSoccerPlayer.stats.remove(stats);
                    // Logger.debug("------> OptaMatchEventId({}): Fecha({}): stats modificadas !!!", optaMatchEventId, GlobalDate.getCurrentDate().toString());
                    break;
                }
            }

            // Generamos las nuevas estadísticas del partido para este futbolista
            SoccerTeam opponentTeam = soccerTeam.templateSoccerTeamId.equals(soccerTeamA.templateSoccerTeamId) ? soccerTeamB : soccerTeamA;
            SoccerPlayerStats soccerPlayerStats = new SoccerPlayerStats(startDate, soccerPlayer.optaPlayerId, optaMatchEventId, opponentTeam.templateSoccerTeamId, getFantasyPoints(soccerPlayer.templateSoccerPlayerId));

            // El futbolista ha jugado en el partido?
            if (soccerPlayerStats.playedMinutes > 0 || !soccerPlayerStats.statsCount.isEmpty()) {

                templateSoccerPlayer.addStats(soccerPlayerStats);

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

    private void insertLivePlayersFromTeam(SoccerTeam soccerTeam) {
        for (SoccerPlayer soccerPlayer : soccerTeam.soccerPlayers) {
            liveFantasyPoints.put(soccerPlayer.templateSoccerPlayerId.toString(), new LiveFantasyPoints());
        }
    }

    public void updateState() {
        updateFantasyPoints();
        updateMatchEventTime(OptaEvent.findLast(optaMatchEventId));
    }

    private void updateMatchEventTime(OptaEvent optaEvent) {
        if (period == null) {
            period = PeriodType.PRE_GAME;
        }

        if (period == PeriodType.PRE_GAME) {
            if (Model.optaEvents().findOne("{gameId: #, periodId: 1}", optaMatchEventId).as(OptaEvent.class) != null) {
                period = PeriodType.FIRST_HALF;
            }
        }

        if (period == PeriodType.FIRST_HALF) {
            if (Model.optaEvents().findOne("{gameId: #, periodId: 2}", optaMatchEventId).as(OptaEvent.class) != null) {
                period = PeriodType.SECOND_HALF;
            }
        }

        if (period == PeriodType.SECOND_HALF) {
            if (Model.optaEvents().findOne("{gameId: #, periodId: 14}", optaMatchEventId).as(OptaEvent.class) != null) {
                period = PeriodType.POST_GAME;
            }
        }

        switch(period) {
            case PRE_GAME:      minutesPlayed = 0; break;
            case FIRST_HALF:
            case SECOND_HALF:   minutesPlayed = optaEvent.min; break;
            case POST_GAME:     minutesPlayed = 90; break;
        }

        Model.matchEvents().update("{_id: #}", matchEventId).with("{$set: {period: #, minutesPlayed: #}}", period, minutesPlayed);
    }

    /**
     * Calcular y actualizar los puntos fantasy de un determinado partido "live"
     * Opera sobre cada uno de los futbolistas del partido (teamA y teamB)
     */
    private void updateFantasyPoints() {
        // Logger.info("update Live: matchEvent: {}", templateMatchEventId.toString());

        for (SoccerPlayer soccerPlayer : soccerTeamA.soccerPlayers) {
            updateFantasyPoints(soccerPlayer);
        }

        for (SoccerPlayer soccerPlayer : soccerTeamB.soccerPlayers) {
            updateFantasyPoints(soccerPlayer);
        }
    }

    private void updateFantasyPoints(SoccerPlayer soccerPlayer) {
        // Obtener los puntos fantasy obtenidos por el futbolista en un partido
        Iterable<OptaEvent> optaEventResults = Model.optaEvents().find("{optaPlayerId: #, gameId: #}",
                                                                       soccerPlayer.optaPlayerId, optaMatchEventId).as(OptaEvent.class);

        LiveFantasyPoints fantasyPoints = new LiveFantasyPoints();

        // Sumarlos
        for (OptaEvent point : optaEventResults) {
            if (point.points != 0) {
                int prevPoints = 0;
                OptaEventType optaEventType = OptaEventType.getEnum(point.typeId);

                if (fantasyPoints.events.containsKey(optaEventType.name())) {
                    prevPoints = fantasyPoints.events.get(optaEventType.name());
                }

                fantasyPoints.events.put(optaEventType.name(), prevPoints + point.points);
                fantasyPoints.points += point.points;
            }
        }
        // if (points > 0) Logger.info("--> {}: {} = {}", soccerPlayer.optaPlayerId, soccerPlayer.name, points);

        // Actualizar sus puntos en cada MatchEvent en el que participe
        setLiveFantasyPointsOfSoccerPlayer(optaMatchEventId, soccerPlayer.templateSoccerPlayerId.toString(), fantasyPoints);
    }

    static private void setLiveFantasyPointsOfSoccerPlayer(String optaMatchId, String soccerPlayerId, LiveFantasyPoints fantasyPoints) {
        //Logger.info("setLiveFantasyPoints: {} = {} fantasy points", soccerPlayerId, points);

        String searchPattern = String.format("{optaMatchEventId: #, 'liveFantasyPoints.%s': {$exists: 1}}", soccerPlayerId);
        String setPattern = String.format("{$set: {'liveFantasyPoints.%s': #}}", soccerPlayerId);
        Model.matchEvents()
                .update(searchPattern, optaMatchId)
                .multi()
                .with(setPattern, fantasyPoints);
    }
}

class LiveFantasyPoints {
    public int points;                                          // Puntos totales de un SoccerPlayer
    public HashMap<String, Integer> events = new HashMap<>();   // OptaEventType.name => fantasyPoints conseguidos gracias a el
}
