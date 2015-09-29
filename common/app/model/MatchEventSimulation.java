package model;

import model.opta.OptaEventType;
import org.bson.types.ObjectId;
import play.Logger;

import java.util.*;
import java.util.stream.Collectors;

public class MatchEventSimulation {
    enum TEAM {
        HOME,
        AWAY,
        MAX
    };

    final String PLAYED_MATCHES = "PLAYED_MATCHES";
    final String PLAYED_MINUTES = "PLAYED_MINUTES";
    final int TICK_SECONDS = 10;

    public int homeScore = 0;
    public int awayScore = 0;
    public ArrayList<SimulationEvent> simulationEvents = new ArrayList<>();

    public MatchEventSimulation(ObjectId aTemplateMatchEventId) {
        long startTime = System.currentTimeMillis();

        templateMatchEventId = aTemplateMatchEventId;

        Logger.debug("MatchEventSimulation {}: init", templateMatchEventId);

        TemplateMatchEvent templateMatchEvent = TemplateMatchEvent.findOne(templateMatchEventId);
        optaMatchEventId = templateMatchEvent.optaMatchEventId;
        homeTeamId = templateMatchEvent.optaTeamAId;
        awayTeamId = templateMatchEvent.optaTeamBId;
        competitionId = templateMatchEvent.optaCompetitionId;
        seasonId = templateMatchEvent.optaSeasonId;

        timestamp = GlobalDate.getCurrentDate();
        periodId = 0;
        min = 0;
        sec = 0;

        pointsTranslationMap = new HashMap<>();

        for (PointsTranslation pointTranslation : PointsTranslation.getAllCurrent()) {
            pointsTranslationMap.put(pointTranslation.eventTypeId, pointTranslation);
        }

        // Los futbolistas que componen los equipos
        players.put(TEAM.HOME, TemplateSoccerPlayer.findAllFromTemplateTeam(templateMatchEvent.templateSoccerTeamAId));
        registerEvents(players.get(TEAM.HOME));

        players.put(TEAM.AWAY, TemplateSoccerPlayer.findAllFromTemplateTeam(templateMatchEvent.templateSoccerTeamBId));
        registerEvents(players.get(TEAM.AWAY));

        // La alineación inicial de cada uno de los equipos
        lineUp.put(TEAM.HOME, selectLineUp(players.get(TEAM.HOME)));
        lineUp.put(TEAM.AWAY, selectLineUp(players.get(TEAM.AWAY)));

        // La prioridad de los futbolistas de la alineación de participar en el partido
        priorities.put(TEAM.HOME, calculatePriorities(lineUp.get(TEAM.HOME)));
        prioritiesTotal.put(TEAM.HOME, 1);
        priorities.get(TEAM.HOME).forEach((key, priority) -> {
            prioritiesTotal.put(TEAM.HOME, prioritiesTotal.get(TEAM.HOME) + priority);
        });

        priorities.put(TEAM.AWAY, calculatePriorities(lineUp.get(TEAM.AWAY)));
        prioritiesTotal.put(TEAM.AWAY, 1);
        priorities.get(TEAM.AWAY).forEach((key, priority) -> {
            prioritiesTotal.put(TEAM.AWAY, prioritiesTotal.get(TEAM.AWAY) + priority);
        });

        startGame();
        runGame();

        Logger.debug("MatchEventSimulation: elapsed: {}", System.currentTimeMillis() - startTime);
    }

    void runGame() {
        // Recorremos las 2 partes
        for (int p=0; p<2; p++) {
            if (p == 1) {
                secondHalfGame();
            }
            for (int m = 0; m < 45; m++) {
                for (int s = 0; s < 60; s += TICK_SECONDS) {
                    min = 45 * p + m;
                    sec = s;
                    updateGameTick();
                }
            }
        }

        stopGame();

        // OptaMatchEventStats.updateMatchResult(optaMatchEventId, homeScore, awayScore);
        // new OptaMatchEventChangeProcessor(optaMatchEventId).process();
    }

    void updateGameTick() {
        if (teamWithBall == null) {
            teamWithBall = teamBegins();
        }

        // Quién tiene el balón
        playerWithBall = getTarget();

        // Qué acción realiza
        OptaEventType action = selectAction(playerWithBall);

        // String optaTeam = teamWithBall.equals(TEAM.HOME) ? homeTeamId : awayTeamId;
        // createEvent(action.code, optaTeam, playerWithBall.optaPlayerId).insert().markAsSimulated();
        createEvent(action, playerWithBall);

        // Gol?
        if (goalEvents.contains(action)) {
            if (teamWithBall.equals(TEAM.HOME))
                homeScore++;
            else
                awayScore++;
            // OptaMatchEventStats.updateMatchResult(optaMatchEventId, homeScore, awayScore);
        }

        // Pérdida de balón?
        if (changeTeamEvents.contains(action)) {
            teamWithBall = changeTeam();
        }
    }

    void startGame() {
        periodId = 1;   // START_GAME
        // createEvent(OptaEventType.PERIOD_BEGINS.code).insert();
    }

    void secondHalfGame() {
        periodId = 2;
        // createEvent(OptaEventType.PERIOD_BEGINS.code).insert();
    }

    void stopGame() {
        periodId = 14;  // POST_GAME;
        // createEvent(OptaEventType.GAME_END.code).insert();

    }

    TEAM teamBegins() {
        return TEAM.HOME;
    }

    TEAM changeTeam() {
        return teamWithBall.equals(TEAM.HOME) ? TEAM.AWAY : TEAM.HOME;
    }

    TemplateSoccerPlayer getTarget() {
        TemplateSoccerPlayer target = null;

        // Lanzamos un dado (cada jugador tiene como mínimo 1 número, aunque no tenga prioridades)
        int die = rnd.nextInt(prioritiesTotal.get(teamWithBall) + priorities.get(teamWithBall).size());
        for (Map.Entry<TemplateSoccerPlayer, Integer> entry : priorities.get(teamWithBall).entrySet()) {
            die -= entry.getValue() + 1;
            if (die <= 0) {
                target = entry.getKey();
                break;
            }
        }
        return target;
    }

    OptaEventType selectAction(TemplateSoccerPlayer player) {
        OptaEventType action = OptaEventType.PASS_SUCCESSFUL;

        HashMap<String, Integer> stats = statsCount.get(player.templateSoccerPlayerId);
        int total = 0;
        for (OptaEventType optaEventType : attackEvents) {
            total += getStat(stats, optaEventType);
        }

        if (total > 0) {
            int die = rnd.nextInt(total);
            for (OptaEventType optaEventType : attackEvents) {
                die -= getStat(stats, optaEventType);
                if (die <= 0) {
                    action = optaEventType;
                    break;
                }
            }
        }
        else {
            action = OptaEventType.PASS_UNSUCCESSFUL;
        }

        return action;
    }

    Map<TemplateSoccerPlayer, Integer> calculatePriorities(List<TemplateSoccerPlayer> players) {
        Map<TemplateSoccerPlayer, Integer> priorities = new HashMap<>();
        int total = 0;
        for (TemplateSoccerPlayer player : players) {
            HashMap<String, Integer> stats = statsCount.get(player.templateSoccerPlayerId);
            int priority = 0;
            for (OptaEventType optaEventType : attackEvents) {
                priority += getStat(stats, optaEventType);
            }
            priorities.put(player, priority);
            total += priority;
        }
        /*
        for (TemplateSoccerPlayer player : players) {
            Logger.info("{}: {}%", player.name, priorities.get(player)*100/total);
        }
        */
        return priorities;
    }

    int getStat(HashMap<String, Integer> stats, OptaEventType optaEventType) {
        return stats.containsKey(optaEventType.toString()) ? stats.get(optaEventType.toString()) : 0;
    }

    void registerEvents(TemplateSoccerPlayer player) {
        if (!statsCount.containsKey(player.templateSoccerPlayerId)) {
            statsCount.put(player.templateSoccerPlayerId, new HashMap<>());
        }
        HashMap<String, Integer> stats = statsCount.get(player.templateSoccerPlayerId);

        stats.put(PLAYED_MATCHES, player.getPlayedMatches());

        int minutes = player.stats.stream().mapToInt(s -> s.playedMinutes).sum();
        stats.put(PLAYED_MINUTES, minutes);

        StringBuilder info = new StringBuilder();
        info.append(String.format("%s (%s) matches: %d \n",
                player.name, player.templateSoccerPlayerId.toString(), player.getPlayedMatches()));

        for (OptaEventType optaEventType : OptaEventType.values()) {
            int count = (int) Model.optaEvents().count("{optaPlayerId: #, typeId: #}",
                    player.optaPlayerId, optaEventType.code);
            if (count > 0) {
                stats.put(optaEventType.toString(), count);
                info.append(optaEventType).append(" : ").append(count).append(" \n");
            }
        }

        // Logger.info(info.toString());
    }

    void registerEvents(List<TemplateSoccerPlayer> players) {
        players.forEach(this::registerEvents);
    }

    List<TemplateSoccerPlayer> selectPlayers(List<TemplateSoccerPlayer> players, int num) {
        List<TemplateSoccerPlayer> selected = new ArrayList<>();

        List<TemplateSoccerPlayer> sorted = players.stream().sorted((e1, e2) -> {
            HashMap<String, Integer> stats1 = statsCount.containsKey(e1.templateSoccerPlayerId)
                    ? statsCount.get(e1.templateSoccerPlayerId)
                    : null;
            HashMap<String, Integer> stats2 = statsCount.containsKey(e2.templateSoccerPlayerId)
                    ? statsCount.get(e2.templateSoccerPlayerId)
                    : null;

            if (stats1 == null) return -1;
            else if (stats2 == null) return 1;
            return Integer.compare(stats1.get(PLAYED_MINUTES), stats2.get(PLAYED_MINUTES));
        }).collect(Collectors.toList());

        Collections.reverse(sorted);
        selected.addAll(sorted.subList(0, num));

        return selected;
    }

    List<TemplateSoccerPlayer> filterByFieldPos(FieldPos fieldPos, List<TemplateSoccerPlayer> players) {
        return players.stream().filter(p -> p.fieldPos.equals(fieldPos)).collect(Collectors.toList());
    }

    List<TemplateSoccerPlayer> selectLineUp(List<TemplateSoccerPlayer> players) {
        List<TemplateSoccerPlayer> selected = new ArrayList<>();
        selected.addAll(selectPlayers(filterByFieldPos(FieldPos.GOALKEEPER, players), 1));
        selected.addAll(selectPlayers(filterByFieldPos(FieldPos.DEFENSE, players), 4));
        selected.addAll(selectPlayers(filterByFieldPos(FieldPos.MIDDLE, players), 4));
        selected.addAll(selectPlayers(filterByFieldPos(FieldPos.FORWARD, players), 2));
        return selected;
    }

    void createEvent(OptaEventType eventType, TemplateSoccerPlayer soccerPlayer) {
        // Obtenemos los puntos que vale el evento
        PointsTranslation pointsTranslation = pointsTranslationMap.get(eventType.code);
        int points = (pointsTranslation != null) ? pointsTranslation.points : 0;

        Logger.debug("MatchEventSimulation: {} createEvent: {} min: {} sec: {} points: {}", templateMatchEventId.toString(), eventType.name(), min, sec, points);

        // Creamos el evento simulado
        SimulationEvent simulationEvent = new SimulationEvent();
        simulationEvent.homeScore = homeScore;
        simulationEvent.awayScore = awayScore;
        simulationEvent.min = min;
        simulationEvent.sec = sec;
        simulationEvent.templateSoccerPlayerId = soccerPlayer.templateSoccerPlayerId;
        simulationEvent.eventType = eventType;
        simulationEvent.points = points;
        simulationEvents.add(simulationEvent);
    }

    static OptaEventType[] attackEvents = {
            OptaEventType.PASS_SUCCESSFUL, OptaEventType.TAKE_ON, OptaEventType.ASSIST, OptaEventType.FOUL_RECEIVED,
            OptaEventType.PASS_UNSUCCESSFUL, OptaEventType.DISPOSSESSED, OptaEventType.ERROR, OptaEventType.DECISIVE_ERROR,
            OptaEventType.ATTEMPT_SAVED, OptaEventType.POST, OptaEventType.MISS, OptaEventType.OWN_GOAL,
            OptaEventType.CAUGHT_OFFSIDE, OptaEventType.GOAL_SCORED_BY_GOALKEEPER, OptaEventType.GOAL_SCORED_BY_DEFENDER,
            OptaEventType.GOAL_SCORED_BY_MIDFIELDER, OptaEventType.GOAL_SCORED_BY_FORWARD
    };

    static Set<OptaEventType> goalEvents = new HashSet<OptaEventType>() {{
        add(OptaEventType.GOAL_SCORED_BY_GOALKEEPER);
        add(OptaEventType.GOAL_SCORED_BY_DEFENDER);
        add(OptaEventType.GOAL_SCORED_BY_MIDFIELDER);
        add(OptaEventType.GOAL_SCORED_BY_FORWARD);
    }};

    static Set<OptaEventType> changeTeamEvents = new HashSet<OptaEventType>() {{
        add(OptaEventType.PASS_UNSUCCESSFUL);
        add(OptaEventType.DISPOSSESSED);
        add(OptaEventType.ERROR);
        add(OptaEventType.DECISIVE_ERROR);
        add(OptaEventType.ATTEMPT_SAVED);
        add(OptaEventType.POST);
        add(OptaEventType.MISS);
        add(OptaEventType.OWN_GOAL);
        add(OptaEventType.CAUGHT_OFFSIDE);
        add(OptaEventType.GOAL_SCORED_BY_GOALKEEPER);
        add(OptaEventType.GOAL_SCORED_BY_DEFENDER);
        add(OptaEventType.GOAL_SCORED_BY_MIDFIELDER);
        add(OptaEventType.GOAL_SCORED_BY_FORWARD);
    }};

    public ObjectId templateMatchEventId;
    public String optaMatchEventId;
    public String homeTeamId;
    public String awayTeamId;
    public String competitionId;
    public String seasonId;

    public int periodId;
    public Date timestamp;
    public Date lastModified;
    public int min;
    public int sec;

    public int eventId;

    HashMap<Integer, PointsTranslation> pointsTranslationMap;

    Map<TEAM, List<TemplateSoccerPlayer>> players = new HashMap<>();
    Map<TEAM, List<TemplateSoccerPlayer>> lineUp = new HashMap<>();
    Map<TEAM, Map<TemplateSoccerPlayer, Integer>> priorities = new HashMap<>();
    Map<TEAM, Integer> prioritiesTotal = new HashMap<>();

    TEAM teamWithBall;
    TemplateSoccerPlayer playerWithBall;

    Random rnd = new Random();

    private HashMap<ObjectId, HashMap<String, Integer>> statsCount = new HashMap<>();
}
