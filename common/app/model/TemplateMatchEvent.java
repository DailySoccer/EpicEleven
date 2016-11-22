package model;

import com.fasterxml.jackson.annotation.JsonView;
import com.mongodb.WriteConcern;
import model.opta.OptaEvent;
import model.opta.OptaEventType;
import model.opta.OptaMatchEvent;
import model.opta.OptaMatchEventStats;
import org.bson.types.ObjectId;
import org.joda.time.DateTime;
import org.joda.time.Minutes;
import org.jongo.marshall.jackson.oid.Id;
import play.Logger;
import utils.ListUtils;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collector;
import java.util.stream.Collectors;

public class TemplateMatchEvent implements JongoId {
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

    @JsonView(JsonViews.FullContest.class)
    public int homeScore = -1;
    @JsonView(JsonViews.FullContest.class)
    public int awayScore = -1;

    // Asocia un soccerPlayerId con fantasyPoints
    @JsonView(JsonViews.FullContest.class)
    public HashMap<String, LiveFantasyPoints> liveFantasyPoints = new HashMap<>();

    @JsonView(JsonViews.Public.class)
    public PeriodType period = PeriodType.PRE_GAME;

    @JsonView(JsonViews.Public.class)
    public int minutesPlayed;

    @JsonView(value={JsonViews.ContestInfo.class, JsonViews.Extended.class, JsonViews.CreateContest.class})
    public Date startDate;

    @JsonView(JsonViews.NotForClient.class)
    public Date createdAt;

    @JsonView(JsonViews.NotForClient.class)
    public Date gameStartedDate;

    @JsonView(JsonViews.NotForClient.class)
    public Date gameFinishedDate;

    @JsonView(JsonViews.NotForClient.class)
    public HashSet<String> pendingTasks = new HashSet();

    @JsonView(JsonViews.NotForClient.class)
    public boolean simulation = false;

    @JsonView(JsonViews.NotForClient.class)
    public ArrayList<SimulationEvent> simulationEvents = new ArrayList<>();

    public TemplateMatchEvent() { }

    public TemplateMatchEvent copy() {
        TemplateMatchEvent templateMatchEvent = new TemplateMatchEvent();

        templateMatchEvent.optaMatchEventId = this.optaMatchEventId;
        templateMatchEvent.optaCompetitionId = this.optaCompetitionId;
        templateMatchEvent.optaSeasonId = this.optaSeasonId;
        templateMatchEvent.templateSoccerTeamAId = this.templateSoccerTeamAId;
        templateMatchEvent.optaTeamAId = this.optaTeamAId;
        templateMatchEvent.templateSoccerTeamBId = this.templateSoccerTeamBId;
        templateMatchEvent.optaTeamBId = this.optaTeamBId;

        templateMatchEvent.startDate = this.startDate;
        templateMatchEvent.createdAt = GlobalDate.getCurrentDate();

        templateMatchEvent.simulation = this.simulation;

        return templateMatchEvent;
    }

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

    public static long countClonesFromOptaId(String optaMatchEventId) {
        return Model.templateMatchEvents().count("{optaMatchEventId: { $regex: # }}", String.format("^%s#*+", optaMatchEventId));
    }

    public static List<TemplateMatchEvent> findAll(List<ObjectId> idList) {
        return ListUtils.asList(Model.findObjectIds(Model.templateMatchEvents(), "_id", idList).as(TemplateMatchEvent.class));
    }

    public static List<TemplateMatchEvent> findAllSimulationsToSetup() {
        return ListUtils.asList(Model.templateMatchEvents().find("{simulation: true, period: 'PRE_GAME', \"simulationEvents.0\": {$exists: 0}}").as(TemplateMatchEvent.class));
    }

    public static List<TemplateMatchEvent> findAllSimulationsByStartDate() {
        if (Model.isLocalHostTargetEnvironment()) {
            return ListUtils.asList(Model.templateMatchEvents().find("{simulation: true, period: 'PRE_GAME', startDate: {$gt: #, $lte: #}}",
                    new DateTime(GlobalDate.getCurrentDate()).minusDays(1).toDate(), GlobalDate.getCurrentDate()).as(TemplateMatchEvent.class));
        }

        return ListUtils.asList(Model.templateMatchEvents().find("{simulation: true, period: 'PRE_GAME', startDate: {$lte: #}}", GlobalDate.getCurrentDate()).as(TemplateMatchEvent.class));
    }

    public static List<TemplateMatchEvent> findAllSimulationsToUpdate() {
        return ListUtils.asList(Model.templateMatchEvents().find("{simulation: true, period: {$in: ['FIRST_HALF', 'SECOND_HALF']}, startDate: {$lte: #}, \"simulationEvents.0\": {$exists: 1}}", GlobalDate.getCurrentDate()).as(TemplateMatchEvent.class));
    }

    public static List<TemplateMatchEvent> findAllPlaying(List<ObjectId> idList) {
        // Un partido se está jugando, si...
        // 1) existe el campo gameStartedDate
        // 2) No existe el campo gameFinishedDate O No ha pasado mucho tiempo desde que terminó (30 minutos)
        //      Se deja el margen "extra", porque gameFinishedDate se crea con la última actualización de Opta (que puede incluir nuevos datos de live)
        Date dateMinusOneHour = new DateTime(GlobalDate.getCurrentDate()).minusMinutes(30).toDate();
        return ListUtils.asList(Model.templateMatchEvents().find(String.format(
                "{$and: [{%s: {$in: #}}, {gameStartedDate: {$exists: 1}}, {$or: [{gameFinishedDate: {$exists: 0}}, {gameFinishedDate: {$gt: #}}]}]}", "_id"
        ), idList, dateMinusOneHour).as(TemplateMatchEvent.class));
    }

    public static TemplateMatchEvent findNextMatchEvent(ObjectId templateSoccerTeamId) {
        return Model.templateMatchEvents().findOne("{$query: " +
                        "{$and: [" +
                            "{startDate: {$gt: #}}," +
                            "{$or: [{templateSoccerTeamAId: #}, {templateSoccerTeamBId: #}]}," +
                            "{gameStartedDate: {$exists: 0}}," +
                            "{simulation: {$ne: true}}" +
                        "]}," +
                        "$orderby: {startDate: 1}}",
                GlobalDate.getCurrentDate(), templateSoccerTeamId, templateSoccerTeamId).as(TemplateMatchEvent.class);
    }

    public static List<TemplateMatchEvent> gatherFromTemplateContests(List<TemplateContest> templateContests) {
        HashSet<ObjectId> matchEventsIds = new HashSet<>();

        for (TemplateContest contest : templateContests) {
            matchEventsIds.addAll(contest.templateMatchEventIds);
        }

        return findAll(new ArrayList<>(matchEventsIds));
    }

    public static List<TemplateMatchEvent> gatherFromContests(List<Contest> contests) {
        HashSet<ObjectId> matchEventsIds = new HashSet<>();

        for (Contest contest : contests) {
            matchEventsIds.addAll(contest.templateMatchEventIds);
        }

        return findAll(new ArrayList<>(matchEventsIds));
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
        Model.templateMatchEvents().update("{_id: #, gameStartedDate: {$exists: 0}}", templateMatchEventId).with("{$set: {gameStartedDate: #}}", gameStartedDate);
    }

    public void setGameFinished() {
        gameFinishedDate = GlobalDate.getCurrentDate();
        Model.templateMatchEvents().update("{_id: #, gameFinishedDate: {$exists: 0}}", templateMatchEventId).with("{$set: {gameFinishedDate: #}}", gameFinishedDate);
    }

    public boolean isGameStarted()  { return gameStartedDate != null;  }
    public boolean isGameFinished() { return gameFinishedDate != null; }
    public boolean isSimulation()   { return simulation; }

    public void setPending(String task) {
        pendingTasks.add(task);
        Model.templateMatchEvents().update(templateMatchEventId).with("{$push: {pendingTasks: #}}", task);
    }

    public void clearPending(String task) {
        pendingTasks.remove(task);
        Model.templateMatchEvents().update(templateMatchEventId).with("{$pull: {pendingTasks: #}}", task);
    }

    public boolean isPending(String task) {
        return pendingTasks.contains(task);
    }

    public int getFantasyPointsForTeam(ObjectId templateSoccerTeamId) {
        int points = 0;
        for (TemplateSoccerPlayer soccerPlayer : TemplateSoccerPlayer.findAllFromTemplateTeam(templateSoccerTeamId)) {
            points += getSoccerPlayerFantasyPoints(soccerPlayer.templateSoccerPlayerId);
        }
        return points;
    }

    public boolean containsTemplateSoccerTeam(ObjectId soccerTeamId) {
        return templateSoccerTeamAId.equals(soccerTeamId) || templateSoccerTeamBId.equals(soccerTeamId);
    }

    public boolean containsTemplateSoccerPlayer(ObjectId soccerPlayerId) {
        return liveFantasyPoints.containsKey(soccerPlayerId.toString());
    }

    public LiveFantasyPoints getLiveFantasyPointsBySoccerPlayer(ObjectId soccerPlayerId) {
        LiveFantasyPoints ret = null;
        if (containsTemplateSoccerPlayer(soccerPlayerId)) {
            ret = liveFantasyPoints.get(soccerPlayerId.toString());
        }
        return ret;
    }

    public int getSoccerPlayerFantasyPoints(ObjectId soccerPlayerId) {
        return containsTemplateSoccerPlayer(soccerPlayerId) ? liveFantasyPoints.get(soccerPlayerId.toString()).points : 0;
    }

    public void saveStats() {
        saveStats(templateSoccerTeamAId, TemplateSoccerPlayer.findAllFromTemplateTeam(templateSoccerTeamAId));
        saveStats(templateSoccerTeamBId, TemplateSoccerPlayer.findAllFromTemplateTeam(templateSoccerTeamBId));
    }

    public void saveStats(ObjectId templateSoccerTeamId, List<TemplateSoccerPlayer> templateSoccerPlayers) {
        for (TemplateSoccerPlayer templateSoccerPlayer : templateSoccerPlayers) {
            // Generamos las estadísticas del partido para este futbolista
            ObjectId teamId = templateSoccerTeamId.equals(templateSoccerTeamAId) ? templateSoccerTeamAId : templateSoccerTeamBId;
            ObjectId opponentTeamId = templateSoccerTeamId.equals(templateSoccerTeamAId) ? templateSoccerTeamBId : templateSoccerTeamAId;
            SoccerPlayerStats soccerPlayerStats = new SoccerPlayerStats(startDate, templateSoccerPlayer.optaPlayerId, optaCompetitionId, optaMatchEventId, teamId, opponentTeamId, getSoccerPlayerFantasyPoints(templateSoccerPlayer.templateSoccerPlayerId));

            templateSoccerPlayer.updateStats(soccerPlayerStats);

            /*
            Logger.debug("saveStats: {}({}) - minutes = {} - points = {} - events({})",
                    soccerPlayer.name, soccerPlayer.optaPlayerId, soccerPlayerStats.playedMinutes, getSoccerPlayerFantasyPoints(soccerPlayer.templateSoccerPlayerId), soccerPlayerStats.events);
            */
        }
    }

    public void setupSimulation() {
        Logger.debug("TemplateMatchEvent: setupSimulation: " + templateMatchEventId.toString());

        MatchEventSimulation simulation = new MatchEventSimulation(templateMatchEventId);

        // Actualizamos los eventos que simulan el partido
        simulationEvents = simulation.simulationEvents;

        Model.templateMatchEvents()
                .update("{_id: #}", templateMatchEventId)
                .with("{$set: {simulationEvents: #}}", simulationEvents);
    }

    public void startSimulation() {
        Logger.debug("TemplateMatchEvent: startSimulation: " + templateMatchEventId.toString());

        // Damos el partido como iniciado
        setGameStarted();
        TemplateContest.actionWhenMatchEventIsStarted(this);
        updateMatchEventTime(PeriodType.FIRST_HALF, 0);

        LiveMatchEventSimulation simulation = new LiveMatchEventSimulation(templateMatchEventId);
        liveFantasyPoints = simulation.liveFantasyPoints;
        Model.templateMatchEvents()
                .update("{_id: #}", templateMatchEventId)
                .with("{$set: {liveFantasyPoints: #}}", liveFantasyPoints);

        // Damos el partido por finalizado
        setGameFinished();
        TemplateContest.actionWhenMatchEventIsFinished(this);
        updateMatchEventTime(PeriodType.POST_GAME, 90);
    }

    public void updateSimulationState(long timeMultiplier) {
        // Averiguar cuánto tiempo queremos simular
        long diff = GlobalDate.getCurrentDate().getTime() - startDate.getTime();
        long minutes = TimeUnit.MILLISECONDS.toMinutes(diff * timeMultiplier);

        // Si el partido ya tiene calculada la simulación para el momento actual...
        if (minutes > 0 && minutes != minutesPlayed) {

            // Logger.debug("TemplateMatchEvent: Simulando: {} min: {}", templateMatchEventId.toString(), minutes);

            // Generar los fantasyPoints y registrar los goles (para los eventos anteriores a los minutos de partido)
            applySimulationEventsAtLiveFantasyPoints(simulationEvents.stream().filter(event -> event.min < minutes).collect(Collectors.toList()));

            // Actualizar los liveFantasyPoints del partido
            Model.templateMatchEvents()
                    .update("{_id: #}", templateMatchEventId)
                    .with("{$set: {liveFantasyPoints: #}}", liveFantasyPoints);

            // Actualizar el marcador
            updateMatchEventResult(homeScore, awayScore);
        }

        // Actualizar los minutos del partido
        if (minutes <= 90) {
            updateMatchEventTime(minutes <= 45 ? PeriodType.FIRST_HALF : PeriodType.SECOND_HALF, (int) minutes);
        }
        else {
            // Damos por finalizado el partido
            // IMPORTANTE: El orden de las acciones es importante!
            setGameFinished();
            TemplateContest.actionWhenMatchEventIsFinished(this);
            updateMatchEventTime(PeriodType.POST_GAME, 90);
        }
    }

    // Actualizar los datos de liveFantasyPoints
    public void applySimulationEventsAtLiveFantasyPoints(List<SimulationEvent> simulationEvents) {
        // Limpiamos todos los fantasyPoints (vamos a regenerarlos)
        liveFantasyPoints = new HashMap<>();

        // Generar los fantasyPoints y registrar los goles
        simulationEvents.forEach(event -> {
            applySimulationEventAtLiveFantasyPoints(event, liveFantasyPoints);
            homeScore = event.homeScore;
            awayScore = event.awayScore;
        });
    }

    static public void applySimulationEventAtLiveFantasyPoints(SimulationEvent event, Map<String, LiveFantasyPoints> liveFantasyPoints) {
        String soccerPlayerIdStr = event.templateSoccerPlayerId.toString();

        // Crear/Obtener los fantasyPoints del soccerPlayer
        LiveFantasyPoints fantasyPoints;

        if (liveFantasyPoints.containsKey(soccerPlayerIdStr)) {
            fantasyPoints = liveFantasyPoints.get(soccerPlayerIdStr);
        }
        else {
            fantasyPoints = new LiveFantasyPoints();
            fantasyPoints.points = 0;
        }

        // Crear/Obtener el eventInfo del soccerPlayer
        LiveEventInfo eventInfo;
        if (fantasyPoints.events.containsKey(event.eventType.name())) {
            eventInfo = fantasyPoints.events.get(event.eventType.name());
        }
        else {
            eventInfo = new LiveEventInfo(0);
        }

        // Incrementar los puntos
        eventInfo.add(new LiveEventInfo(event.points));
        fantasyPoints.points += event.points;

        // Logger.debug("{}: Result: {} min: {} sec: {}", templateMatchEventId.toString(), fantasyPoints.points, min, sec);

        // Actualizar la lista de eventos
        fantasyPoints.events.put(event.eventType.name(), eventInfo);

        // Actualizar la lista de fantasyPoints
        liveFantasyPoints.put(soccerPlayerIdStr, fantasyPoints);
    }

    public void updateState() {
        updateFantasyPoints();
        updateMatchEventTimeFromOptaEvent(OptaEvent.findLast(optaMatchEventId));

        // Obtener las estadísticas del partido para conocer el resultado actual del partido
        OptaMatchEventStats stats = OptaMatchEventStats.findOne(optaMatchEventId);
        if (stats != null) {
            updateMatchEventResult(stats.homeScore, stats.awayScore);
        }
    }

    private void updateMatchEventTimeFromOptaEvent(OptaEvent optaEvent) {
        PeriodType thePeriodType = period;

        if (thePeriodType == null) {
            thePeriodType = PeriodType.PRE_GAME;
        }

        if (thePeriodType == PeriodType.PRE_GAME) {
            if (Model.optaEvents().findOne("{gameId: #, periodId: 1}", optaMatchEventId).as(OptaEvent.class) != null) {
                thePeriodType = PeriodType.FIRST_HALF;
            }
        }

        if (thePeriodType == PeriodType.FIRST_HALF) {
            if (Model.optaEvents().findOne("{gameId: #, periodId: 2}", optaMatchEventId).as(OptaEvent.class) != null) {
                thePeriodType = PeriodType.SECOND_HALF;
            }
        }

        if (thePeriodType == PeriodType.SECOND_HALF) {
            if (Model.optaEvents().findOne("{gameId: #, periodId: 14}", optaMatchEventId).as(OptaEvent.class) != null) {
                thePeriodType = PeriodType.POST_GAME;
            }
        }

        updateMatchEventTime(thePeriodType, optaEvent.min);
    }

    private void updateMatchEventTime(PeriodType thePeriodType, int theMinutesPlayed) {
        period = thePeriodType;

        switch(period) {
            case PRE_GAME:      minutesPlayed = 0; break;
            case FIRST_HALF:
            case SECOND_HALF:   minutesPlayed = theMinutesPlayed; break;
            case POST_GAME:     minutesPlayed = 90; break;
        }

        Model.templateMatchEvents().update(templateMatchEventId).with("{$set: {period: #, minutesPlayed: #}}", period, minutesPlayed);
    }

    private void updateMatchEventResult(int theHomeScore, int theAwayScore) {

        homeScore = theHomeScore;
        awayScore = theAwayScore;

        Model.templateMatchEvents().update(templateMatchEventId).with("{$set: {homeScore: #, awayScore: #}}", homeScore, awayScore);
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
                OptaEventType optaEventType = OptaEventType.getEnum(point.typeId);
                LiveEventInfo eventInfo = new LiveEventInfo(point.points);

                // Si ya tenemos almacenado ese evento, lo añadiremos
                if (fantasyPoints.events.containsKey(optaEventType.name())) {
                    eventInfo.add(fantasyPoints.events.get(optaEventType.name()));
                }

                fantasyPoints.events.put(optaEventType.name(), eventInfo);
                fantasyPoints.points += point.points;
            }
        }
        // if (points > 0) Logger.info("--> {}: {} = {}", soccerPlayer.optaPlayerId, soccerPlayer.name, points);

        if (!fantasyPoints.events.isEmpty() || liveFantasyPoints.containsKey(templateSoccerPlayer.templateSoccerPlayerId.toString())) {
            setLiveFantasyPointsOfSoccerPlayer(optaMatchEventId, templateSoccerPlayer.templateSoccerPlayerId.toString(), fantasyPoints);
        }
    }

    private void setLiveFantasyPointsOfSoccerPlayer(String optaMatchId, String soccerPlayerId, LiveFantasyPoints fantasyPoints) {
        //Logger.info("setLiveFantasyPoints: {} = {} fantasy points", soccerPlayerId, points);

        String setPattern = String.format("{$set: {'liveFantasyPoints.%s': #}}", soccerPlayerId);
        Model.templateMatchEvents()
                .update("{optaMatchEventId: #}", optaMatchId)
                .multi()
                .with(setPattern, fantasyPoints);

        liveFantasyPoints.put(soccerPlayerId, fantasyPoints);
    }
    
    public static TemplateMatchEvent createFromOpta(OptaMatchEvent optaMatchEvent) {
        TemplateMatchEvent templateMatchEvent = null;

        TemplateSoccerTeam teamA = TemplateSoccerTeam.findOneFromOptaId(optaMatchEvent.homeTeamId);
        TemplateSoccerTeam teamB = TemplateSoccerTeam.findOneFromOptaId(optaMatchEvent.awayTeamId);

        if (teamA != null && teamB != null) {
            Logger.info("Template MatchEvent: {} vs {} ({})", teamA.name, teamB.name, GlobalDate.formatDate(optaMatchEvent.matchDate));

            templateMatchEvent = new TemplateMatchEvent();
            templateMatchEvent.startDate = optaMatchEvent.matchDate;
            templateMatchEvent.optaMatchEventId = optaMatchEvent.optaMatchEventId;
            templateMatchEvent.optaCompetitionId = optaMatchEvent.competitionId;
            templateMatchEvent.optaSeasonId = optaMatchEvent.seasonId;
            templateMatchEvent.templateSoccerTeamAId = teamA.templateSoccerTeamId;
            templateMatchEvent.optaTeamAId = teamA.optaTeamId;
            templateMatchEvent.templateSoccerTeamBId = teamB.templateSoccerTeamId;
            templateMatchEvent.optaTeamBId = teamB.optaTeamId;
            templateMatchEvent.createdAt = GlobalDate.getCurrentDate();
        }

        return templateMatchEvent;
    }

    public void changeDocument(OptaMatchEvent optaMatchEvent) {
        startDate = optaMatchEvent.matchDate;
        // optaMatchEventId = optaMatchEvent.optaMatchEventId;
        optaCompetitionId = optaMatchEvent.competitionId;
        optaSeasonId = optaMatchEvent.seasonId;
        updateDocument();
    }

    public void insert() {
        Model.templateMatchEvents().withWriteConcern(WriteConcern.SAFE).update("{_id: #}", templateMatchEventId).upsert().with(this);
    }

    public void updateDocument() {
        Model.templateMatchEvents().withWriteConcern(WriteConcern.SAFE).update("{optaMatchEventId: #}", optaMatchEventId).upsert().with(this);
    }

    static public boolean importMatchEvent(OptaMatchEvent optaMatchEvent) {
        TemplateMatchEvent templateMatchEvent = createFromOpta(optaMatchEvent);
        if (templateMatchEvent != null) {
            templateMatchEvent.updateDocument();
        }
        else {
            Logger.error("Ignorando OptaMatchEvent: {} ({})", optaMatchEvent.optaMatchEventId, GlobalDate.formatDate(optaMatchEvent.matchDate));
            return false;
        }
        return true;
    }

    static public List<ObjectId> createSimulationsWithStartDate(List<ObjectId> templateMatchEventList, Date startDateModified) {
        return templateMatchEventList.stream().map(matchEventId -> {
            TemplateMatchEvent simulateMatchEvent = TemplateMatchEvent.createSimulationWithStartDate(matchEventId, startDateModified);
            return simulateMatchEvent.templateMatchEventId;
        }).collect(Collectors.toList());
    }

    static public TemplateMatchEvent createSimulationWithStartDate(ObjectId matchEventId, Date startDateModified) {
        Logger.debug("TemplateMatchEvent.CreateSimulationWithStartDate: " + matchEventId.toString() + " startDate: " + startDateModified.toString());

        TemplateMatchEvent templateMatchEvent = TemplateMatchEvent.findOne(matchEventId);

        /*
        long nextMatchEvent = TemplateMatchEvent.countClonesFromOptaId(templateMatchEvent.optaMatchEventId);
        OptaMatchEvent cloneOptaMatchEvent = OptaMatchEvent.findOne(templateMatchEvent.optaMatchEventId).copy();
        cloneOptaMatchEvent.optaMatchEventId = String.format("%s#%d", templateMatchEvent.optaMatchEventId, nextMatchEvent);
        cloneOptaMatchEvent.insert();
        */

        TemplateMatchEvent virtualMatchEvent = templateMatchEvent.copy();
        virtualMatchEvent.templateMatchEventId = new ObjectId();
        virtualMatchEvent.startDate = startDateModified;
        // Marcamos el identificador del partido de Opta con un flag. De tal forma que no se considere el partido clonado como asociado con el de Opta
        virtualMatchEvent.optaMatchEventId = virtualMatchEvent.optaMatchEventId + "#";
        virtualMatchEvent.simulation = true;
        virtualMatchEvent.insert();

        return virtualMatchEvent;
    }

    private void insertLivePlayers(List<TemplateSoccerPlayer> soccerPlayers) {
        for (TemplateSoccerPlayer soccerPlayer : soccerPlayers) {
            liveFantasyPoints.put(soccerPlayer.templateSoccerPlayerId.toString(), new LiveFantasyPoints());
        }
    }

    static public boolean isInvalidFromImport(OptaMatchEvent optaMatchEvent) {
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