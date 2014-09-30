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

public class TemplateMatchEvent implements JongoId, Initializer {
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
    public ObjectId templateMatchEventId;

    @JsonView(JsonViews.NotForClient.class)
    public String optaMatchEventId;
    @JsonView(JsonViews.NotForClient.class)
    public String optaCompetitionId;
    @JsonView(JsonViews.NotForClient.class)
    public String optaSeasonId;
    @JsonView(JsonViews.NotForClient.class)
    public String optaTeamAId;
    @JsonView(JsonViews.NotForClient.class)
    public String optaTeamBId;

    public ObjectId templateSoccerTeamAId;
    public ObjectId templateSoccerTeamBId;

    // Asocia un soccerPlayerId con fantasyPoints
    @JsonView(JsonViews.FullContest.class)
    public HashMap<String, LiveFantasyPoints> liveFantasyPoints = new HashMap<>();

    @JsonView(JsonViews.Public.class)
    public PeriodType period = PeriodType.PRE_GAME;

    @JsonView(JsonViews.Public.class)
    public int minutesPlayed;

    @JsonView(JsonViews.Extended.class)
    public Date startDate;

    @JsonView(JsonViews.NotForClient.class)
    public Date createdAt;

    @JsonView(JsonViews.NotForClient.class)
    public Date gameStartedDate;

    @JsonView(JsonViews.NotForClient.class)
    public Date gameFinishedDate;

    public TemplateMatchEvent() { }

    public void Initialize() { }

    public ObjectId getId() {
        return templateMatchEventId;
    }

    public boolean isPostGame() {
        return period.equals(PeriodType.POST_GAME);
    }

    public boolean hasChanged(OptaMatchEvent optaMatchEvent) {
        return !optaMatchEventId.equals(optaMatchEvent.optaMatchEventId) ||
               !optaTeamAId.equals(optaMatchEvent.homeTeamId) ||
               !optaTeamBId.equals(optaMatchEvent.awayTeamId) ||
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

    public List<TemplateSoccerPlayer> getTemplateSoccerPlayers() {
        List<TemplateSoccerPlayer> templateSoccerPlayers = new ArrayList<>();
        templateSoccerPlayers.addAll(TemplateSoccerPlayer.findAllFromTemplateTeam(templateSoccerTeamAId));
        templateSoccerPlayers.addAll(TemplateSoccerPlayer.findAllFromTemplateTeam(templateSoccerTeamBId));
        return templateSoccerPlayers;
    }

    public List<TemplateSoccerPlayer> getTemplateSoccerPlayersActives() {
        List<TemplateSoccerPlayer> templateSoccerPlayers = new ArrayList<>();
        templateSoccerPlayers.addAll(TemplateSoccerPlayer.findAllActiveFromTemplateTeam(templateSoccerTeamAId));
        templateSoccerPlayers.addAll(TemplateSoccerPlayer.findAllActiveFromTemplateTeam(templateSoccerTeamBId));
        return templateSoccerPlayers;
    }

    public void setGameStarted() {
        gameStartedDate = GlobalDate.getCurrentDate();
        Model.templateMatchEvents().update(templateMatchEventId).with("{$set: {gameStartedDate: #}}", gameStartedDate);
    }

    public void setGameFinished() {
        gameFinishedDate = GlobalDate.getCurrentDate();
        Model.templateMatchEvents().update(templateMatchEventId).with("{$set: {gameFinishedDate: #}}", gameFinishedDate);
    }

    public boolean isGameStarted()  { return gameStartedDate != null;  }
    public boolean isGameFinished() { return gameFinishedDate != null; }

    public boolean isStarted()  {
        MatchEvent matchEvent = MatchEvent.findOneFromTemplate(templateMatchEventId);
        return (matchEvent != null) && matchEvent.isGameStarted();
    }

    public boolean isFinished() {
        MatchEvent matchEvent = MatchEvent.findOneFromTemplate(templateMatchEventId);
        return (matchEvent != null) && matchEvent.isGameFinished();
    }

    public int getFantasyPointsForTeam(ObjectId templateSoccerTeamId) {
        int points = 0;
        for (TemplateSoccerPlayer soccerPlayer : TemplateSoccerPlayer.findAllFromTemplateTeam(templateSoccerTeamId)) {
            points += getSoccerPlayerFantasyPoints(soccerPlayer.templateSoccerPlayerId);
        }
        return points;
    }

    public boolean containsTemplateSoccerPlayer(ObjectId soccerPlayerId) {
        return liveFantasyPoints.containsKey(soccerPlayerId.toString());
    }

    public int getSoccerPlayerFantasyPoints(ObjectId soccerPlayerId) {
        return liveFantasyPoints.get(soccerPlayerId.toString()).points;
    }

    public void saveStats() {
        saveStats(templateSoccerTeamAId, TemplateSoccerPlayer.findAllFromTemplateTeam(templateSoccerTeamAId));
        saveStats(templateSoccerTeamBId, TemplateSoccerPlayer.findAllFromTemplateTeam(templateSoccerTeamBId));
    }

    public void saveStats(ObjectId templateSoccerTeamId, List<TemplateSoccerPlayer> soccersPlayers) {
        for (TemplateSoccerPlayer soccerPlayer : soccersPlayers) {
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
            ObjectId opponentTeamId = templateSoccerTeamId.equals(templateSoccerTeamAId) ? templateSoccerTeamBId : templateSoccerTeamAId;
            SoccerPlayerStats soccerPlayerStats = new SoccerPlayerStats(startDate, soccerPlayer.optaPlayerId, optaMatchEventId, opponentTeamId, getSoccerPlayerFantasyPoints(soccerPlayer.templateSoccerPlayerId));

            // El futbolista ha jugado en el partido?
            if (soccerPlayerStats.playedMinutes > 0 || !soccerPlayerStats.statsCount.isEmpty()) {

                templateSoccerPlayer.addStats(soccerPlayerStats);

                /*
                Logger.debug("saveStats: {}({}) - minutes = {} - points = {} - events({})",
                        soccerPlayer.name, soccerPlayer.optaPlayerId, soccerPlayerStats.playedMinutes, getSoccerPlayerFantasyPoints(soccerPlayer.templateSoccerPlayerId), soccerPlayerStats.events);
                */
            }
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

        Model.templateMatchEvents().update(templateMatchEventId).with("{$set: {period: #, minutesPlayed: #}}", period, minutesPlayed);
    }

    /**
     * Calcular y actualizar los puntos fantasy de un determinado partido "live"
     * Opera sobre cada uno de los futbolistas del partido (teamA y teamB)
     */
    private void updateFantasyPoints() {
        // Logger.info("update Live: matchEvent: {}", templateMatchEventId.toString());

        for (TemplateSoccerPlayer tempateSoccerPlayer : TemplateSoccerPlayer.findAllFromTemplateTeam(templateSoccerTeamAId)) {
            updateFantasyPoints(tempateSoccerPlayer);
        }

        for (TemplateSoccerPlayer tempateSoccerPlayer : TemplateSoccerPlayer.findAllFromTemplateTeam(templateSoccerTeamBId)) {
            updateFantasyPoints(tempateSoccerPlayer);
        }
    }

    private void updateFantasyPoints(TemplateSoccerPlayer templateSoccerPlayer) {
        // Obtener los puntos fantasy obtenidos por el futbolista en un partido
        Iterable<OptaEvent> optaEventResults = Model.optaEvents().find("{optaPlayerId: #, gameId: #}",
                templateSoccerPlayer.optaPlayerId, optaMatchEventId).as(OptaEvent.class);

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
        setLiveFantasyPointsOfSoccerPlayer(optaMatchEventId, templateSoccerPlayer.templateSoccerPlayerId.toString(), fantasyPoints);
    }

    static private void setLiveFantasyPointsOfSoccerPlayer(String optaMatchId, String soccerPlayerId, LiveFantasyPoints fantasyPoints) {
        //Logger.info("setLiveFantasyPoints: {} = {} fantasy points", soccerPlayerId, points);

        String searchPattern = String.format("{optaMatchEventId: #, 'liveFantasyPoints.%s': {$exists: 1}}", soccerPlayerId);
        String setPattern = String.format("{$set: {'liveFantasyPoints.%s': #}}", soccerPlayerId);
        Model.templateMatchEvents()
                .update(searchPattern, optaMatchId)
                .multi()
                .with(setPattern, fantasyPoints);
    }


    static public boolean importMatchEvent(OptaMatchEvent optaMatchEvent) {
        TemplateSoccerTeam teamA = TemplateSoccerTeam.findOneFromOptaId(optaMatchEvent.homeTeamId);
        TemplateSoccerTeam teamB = TemplateSoccerTeam.findOneFromOptaId(optaMatchEvent.awayTeamId);

        if (teamA != null && teamB != null) {
            create(optaMatchEvent, teamA, teamB, optaMatchEvent.matchDate);

            Model.optaMatchEvents().update("{id: #}", optaMatchEvent.optaMatchEventId).with("{$set: {dirty: false}}");
        }
        else {
            Logger.error("Ignorando OptaMatchEvent: {} ({})", optaMatchEvent.optaMatchEventId, GlobalDate.formatDate(optaMatchEvent.matchDate));
            return false;
        }
        return true;
    }

    public void instanciate() {
        insertLivePlayers(TemplateSoccerPlayer.findAllActiveFromTemplateTeam(templateSoccerTeamAId));
        insertLivePlayers(TemplateSoccerPlayer.findAllActiveFromTemplateTeam(templateSoccerTeamBId));
        Model.templateMatchEvents().update(templateMatchEventId).with("{$set: {liveFantasyPoints: #}}", liveFantasyPoints);
    }

    private void insertLivePlayers(List<TemplateSoccerPlayer> soccerPlayers) {
        for (TemplateSoccerPlayer soccerPlayer : soccerPlayers) {
            liveFantasyPoints.put(soccerPlayer.templateSoccerPlayerId.toString(), new LiveFantasyPoints());
        }
    }

    static private TemplateMatchEvent create(OptaMatchEvent optaMatchEvent, TemplateSoccerTeam teamA, TemplateSoccerTeam teamB, Date startDate) {
        Logger.info("Template MatchEvent: {} vs {} ({})", teamA.name, teamB.name, GlobalDate.formatDate(startDate));

        TemplateMatchEvent templateMatchEvent = new TemplateMatchEvent();
        templateMatchEvent.startDate = startDate;
        templateMatchEvent.optaMatchEventId = optaMatchEvent.optaMatchEventId;
        templateMatchEvent.optaCompetitionId = optaMatchEvent.competitionId;
        templateMatchEvent.optaSeasonId = optaMatchEvent.seasonId;
        templateMatchEvent.templateSoccerTeamAId = teamA.templateSoccerTeamId;
        templateMatchEvent.optaTeamAId = teamA.optaTeamId;
        templateMatchEvent.templateSoccerTeamBId = teamB.templateSoccerTeamId;
        templateMatchEvent.optaTeamBId = teamB.optaTeamId;
        templateMatchEvent.createdAt = GlobalDate.getCurrentDate();

        Model.templateMatchEvents().update("{optaMatchEventId: #}", optaMatchEvent.optaMatchEventId).upsert().with(templateMatchEvent);

        return templateMatchEvent;
    }

    static public boolean isInvalid(OptaMatchEvent optaMatchEvent) {
        boolean invalid = (optaMatchEvent.homeTeamId == null) || optaMatchEvent.homeTeamId.isEmpty() ||
                          (optaMatchEvent.awayTeamId == null) || optaMatchEvent.awayTeamId.isEmpty();

        if (!invalid) {
            TemplateSoccerTeam teamA = TemplateSoccerTeam.findOneFromOptaId(optaMatchEvent.homeTeamId);
            TemplateSoccerTeam teamB = TemplateSoccerTeam.findOneFromOptaId(optaMatchEvent.awayTeamId);
            invalid = (teamA == null) || (teamB == null);
        }

        return invalid;
    }
}

class LiveFantasyPoints {
    public int points;                                          // Puntos totales de un SoccerPlayer

    @JsonView(JsonViews.FullContest.class)
    public HashMap<String, Integer> events = new HashMap<>();   // OptaEventType.name => fantasyPoints conseguidos gracias a el
}