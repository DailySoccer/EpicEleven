package model;

import model.opta.OptaEvent;
import model.opta.OptaEventType;
import org.bson.types.ObjectId;
import play.Logger;
import utils.ListUtils;

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
    final int TICK_SECONDS = 5;

    public int homeScore = 0;
    public int awayScore = 0;
    public ArrayList<SimulationEvent> simulationEvents = new ArrayList<>();

    public MatchEventSimulation(ObjectId aTemplateMatchEventId) {
        long startTime = System.currentTimeMillis();

        templateMatchEventId = aTemplateMatchEventId;

        Logger.debug("MatchEventSimulation {}: init", templateMatchEventId);

        templateMatchEvent = TemplateMatchEvent.findOne(templateMatchEventId);
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
        registerEvents(players.get(TEAM.HOME), homeTeamId);

        players.put(TEAM.AWAY, TemplateSoccerPlayer.findAllFromTemplateTeam(templateMatchEvent.templateSoccerTeamBId));
        registerEvents(players.get(TEAM.AWAY), awayTeamId);

        // La alineación inicial de cada uno de los equipos
        lineUp.put(TEAM.HOME, selectLineUp(players.get(TEAM.HOME)));
        lineUp.put(TEAM.AWAY, selectLineUp(players.get(TEAM.AWAY)));

        // La prioridad de los futbolistas de la alineación de participar en el partido
        priorities.put(TEAM.HOME, calculatePriorities(lineUp.get(TEAM.HOME), attackEvents));
        prioritiesTotal.put(TEAM.HOME, 1);
        priorities.get(TEAM.HOME).forEach((key, priority) -> {
            prioritiesTotal.put(TEAM.HOME, prioritiesTotal.get(TEAM.HOME) + priority);
        });

        priorities.put(TEAM.AWAY, calculatePriorities(lineUp.get(TEAM.AWAY), attackEvents));
        prioritiesTotal.put(TEAM.AWAY, 1);
        priorities.get(TEAM.AWAY).forEach((key, priority) -> {
            prioritiesTotal.put(TEAM.AWAY, prioritiesTotal.get(TEAM.AWAY) + priority);
        });

        // La prioridad de los futbolistas de la alineación de cometer faltas, obtener tarjetas amarillas/rojas
        foulPriorities.put(TEAM.HOME, calculatePriorities(lineUp.get(TEAM.HOME), foulEvents));
        foulPrioritiesTotal.put(TEAM.HOME, 1);
        foulPriorities.get(TEAM.HOME).forEach((key, priority) -> {
            foulPrioritiesTotal.put(TEAM.HOME, foulPrioritiesTotal.get(TEAM.HOME) + priority);
        });

        foulPriorities.put(TEAM.AWAY, calculatePriorities(lineUp.get(TEAM.AWAY), foulEvents));
        foulPrioritiesTotal.put(TEAM.AWAY, 1);
        foulPriorities.get(TEAM.AWAY).forEach((key, priority) -> {
            foulPrioritiesTotal.put(TEAM.AWAY, foulPrioritiesTotal.get(TEAM.AWAY) + priority);
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
            playerWithBall = getTarget();
        }

        // Qué acción realiza
        OptaEventType action = selectAction(playerWithBall, attackEvents, OptaEventType.PASS_UNSUCCESSFUL);
        createEvent(action, playerWithBall);

        // Gol?
        if (goalEventsSet.contains(action)) {
            if (action == OptaEventType.OWN_GOAL) {
                if (teamWithBall.equals(TEAM.HOME))
                    awayScore++;
                else
                    homeScore++;
            }
            else {
                if (teamWithBall.equals(TEAM.HOME))
                    homeScore++;
                else
                    awayScore++;
            }
            // OptaMatchEventStats.updateMatchResult(optaMatchEventId, homeScore, awayScore);
        }

        // Se ha cometido una falta?
        if (action.equals(OptaEventType.FOUL_RECEIVED)) {
            TemplateSoccerPlayer foulPlayer = getTargetThatFoulCommited(teamWithoutTeam());
            if (foulPlayer != null) {
                OptaEventType foulAction = selectAction(foulPlayer, foulEvents, OptaEventType._INVALID_);
                if (!foulAction.equals(OptaEventType._INVALID_)) {
                    // Logger.debug("<--> Foul <-->");
                    // En primer lugar le marcamos como el que realiza la falta
                    createEvent(OptaEventType.FOUL_COMMITTED, foulPlayer);

                    // TODO: Quitado temporalmente por la gravedad del evento
                    /*
                    // Ha sido penalizado con algo más?
                    if (!foulAction.equals(OptaEventType.FOUL_COMMITTED)) {
                        if (foulAction.equals(OptaEventType.SECOND_YELLOW_CARD) || foulAction.equals(OptaEventType.RED_CARD)) {
                            // TODO: Contabilizar el número de tarjetas amarillas
                            foulAction = OptaEventType.YELLOW_CARD;
                        }
                        createEvent(foulAction, foulPlayer);
                    }
                    */
                }
            }
        }

        // Pérdida de balón?
        if (changeTeamEventsSet.contains(action)) {
            teamWithBall = changeTeam();

            // Decidir quién ha cogido el balón
            playerWithBall = getTarget();

            OptaEventType[] defenseEvents = getDefenseEventsFromAttackEvent(action);
            if (defenseEvents.length > 0) {
                // Generamos el evento por el que se quitó el balón
                OptaEventType defenseAction = selectAction(playerWithBall, defenseEvents, OptaEventType._INVALID_);
                if (!defenseAction.equals(OptaEventType._INVALID_)) {
                    // Logger.debug("<--> Defense <-->");
                    createEvent(defenseAction, playerWithBall);
                }
            }
        }
        else {
            // Si no es un regate...
            if (!action.equals(OptaEventType.TAKE_ON)) {
                // Decidir quién continúa con el balón
                playerWithBall = getTarget();
            }
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
    TEAM teamWithoutTeam () {
        return changeTeam();
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

    TemplateSoccerPlayer getTargetThatFoulCommited(TEAM team) {
        TemplateSoccerPlayer target = null;

        // Lanzamos un dado (cada jugador tiene como mínimo 1 número, aunque no tenga prioridades)
        int die = rnd.nextInt(foulPrioritiesTotal.get(team) + foulPriorities.get(team).size());
        for (Map.Entry<TemplateSoccerPlayer, Integer> entry : foulPriorities.get(team).entrySet()) {
            die -= entry.getValue() + 1;
            if (die <= 0) {
                target = entry.getKey();
                break;
            }
        }

        return target;
    }

    // Seleccionar la acción del player (dada una lista de eventos posibles)
    OptaEventType selectAction(TemplateSoccerPlayer player, OptaEventType[] events, OptaEventType defaultAction) {
        OptaEventType action = defaultAction;

        HashMap<String, Integer> stats = statsCount.get(player.templateSoccerPlayerId);
        int total = 0;
        for (OptaEventType optaEventType : events) {
            total += getStat(stats, optaEventType);
        }

        if (total > 0) {
            int die = rnd.nextInt(total);
            for (OptaEventType optaEventType : events) {
                die -= getStat(stats, optaEventType);
                if (die <= 0) {
                    action = optaEventType;
                    break;
                }
            }
        }
        else {
            Logger.debug("Default Action: {}", defaultAction);
        }

        return action;
    }

    // Calcular las probabilidades de un player de realizar ciertas acciones
    Map<TemplateSoccerPlayer, Integer> calculatePriorities(List<TemplateSoccerPlayer> players, OptaEventType[] events) {
        Map<TemplateSoccerPlayer, Integer> priorities = new HashMap<>();
        int total = 0;
        for (TemplateSoccerPlayer player : players) {
            HashMap<String, Integer> stats = statsCount.get(player.templateSoccerPlayerId);
            int priority = 0;
            for (OptaEventType optaEventType : events) {
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

    // Obtener los eventos de un player durante los partidos (obtenido de los datos de Opta)
    void registerEvents(TemplateSoccerPlayer player, String optaTeamId) {
        if (!statsCount.containsKey(player.templateSoccerPlayerId)) {
            statsCount.put(player.templateSoccerPlayerId, new HashMap<>());
        }
        HashMap<String, Integer> stats = statsCount.get(player.templateSoccerPlayerId);

        stats.put(PLAYED_MATCHES, player.getPlayedMatches());

        int minutes = player.stats.stream().mapToInt(s -> s.playedMinutes).sum();
        stats.put(PLAYED_MINUTES, minutes);

        StringBuilder debugInfo = new StringBuilder();
        debugInfo.append(String.format("%s (%s) matches: %d \n",
                player.name, player.templateSoccerPlayerId.toString(), player.getPlayedMatches()));

        boolean inHome = optaTeamId.equals(homeTeamId);
        String eloString = ListUtils.asString(getTeamsELO(inHome? homeTeamId : awayTeamId, inHome? awayTeamId : homeTeamId));
        // Logger.debug("ELO: {}", eloString);

        for (OptaEventType optaEventType : OptaEventType.values()) {
            // Logger.debug("optaPlayerId: {}, competitionId: {}, typeId: {}", player.optaPlayerId, templateMatchEvent.optaCompetitionId, optaEventType.code);

            /*
            String query = String.format("{optaPlayerId: \"%s\", competitionId: \"%s\", typeId: %d, $or: [\n" +
                    "        {homeTeamId: {$in: [%s]}}, \n" +
                    "        {awayTeamId: {$in: [%s]}}\n" +
                    "    ]}", player.optaPlayerId, templateMatchEvent.optaCompetitionId, optaEventType.code, eloString, eloString);
            */
            // Queremos seleccionar partidos en los que el equipo esté en la misma situación que ahora (juegue en casa o fuera)
            String query = inHome
                            ? String.format("{optaPlayerId: \"%s\", competitionId: \"%s\", typeId: %d, awayTeamId: {$in: [%s]}}",
                                player.optaPlayerId, templateMatchEvent.optaCompetitionId, optaEventType.code, eloString)
                            : String.format("{optaPlayerId: \"%s\", competitionId: \"%s\", typeId: %d, homeTeamId: {$in: [%s]}}",
                                player.optaPlayerId, templateMatchEvent.optaCompetitionId, optaEventType.code, eloString);
            // Logger.debug("Query: {}", query);

            int count = (int) Model.optaEvents().count(query);
            // Logger.debug("{}: {}: {} count", player.name, optaTeamId, count);
            if (count > 0) {
                stats.put(optaEventType.toString(), count);
                debugInfo.append(optaEventType).append(" : ").append(count).append(" \n");
            }
        }

        // Logger.info(debugInfo.toString());
    }

    void registerEvents(List<TemplateSoccerPlayer> players, String optaTeamId) {
        players.forEach(player -> registerEvents(player, optaTeamId));
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

    ArrayList<String> getTeamsELO(String ownerTeam, String opponentTeam) {
        ArrayList<String> result = new ArrayList<>();

        for(TeamELO team : ELO) {
            if (!team.optaId.equals(ownerTeam)) {
                result.add(team.optaId);
            }
        }

        return result;
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

        /*
        Logger.debug("min: {} sec: {} {}: {}: {} points: {}",
                min, sec, soccerPlayer.templateTeamId.toString(), soccerPlayer.name, eventType.name(), points);
        */

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

    OptaEventType[] getDefenseEventsFromAttackEvent(OptaEventType attackEvent) {
        OptaEventType[] result = new OptaEventType[]{};

        if (attackEvent.equals(OptaEventType.PASS_UNSUCCESSFUL)) {
            result = new OptaEventType[]{
                OptaEventType.CLAIM,
                OptaEventType.INTERCEPTION,
                OptaEventType.CLEARANCE
            };
        }
        if (attackEvent.equals(OptaEventType.DISPOSSESSED)) {
            result = new OptaEventType[]{
                    OptaEventType.CLEARANCE,
                    OptaEventType.TACKLE_EFFECTIVE
            };
        }
        if (attackEvent.equals(OptaEventType.ATTEMPT_SAVED)) {
            result = new OptaEventType[]{
                    OptaEventType.CLEARANCE,
                    OptaEventType.SAVE_PLAYER,
                    OptaEventType.SAVE_GOALKEEPER
            };
        }

        return result;
    }

    class TeamELO {
        public String optaId;
        public Integer ELO;
        public TeamELO(String optaId, Integer elo) {
            this.optaId = optaId;
            this.ELO = elo;
        }
    }

    TeamELO[] ELO = new TeamELO[] {
            new TeamELO("178", 2064), // Barcelona
            new TeamELO("186", 2055), // Real Madrid
            new TeamELO("175", 1948), // Atlético de Madrid
            new TeamELO("179", 1865), // Sevilla
            new TeamELO("191", 1863), // Valencia
            new TeamELO("176", 1809), // Celta de Vigo
            new TeamELO("449", 1804), // Villarreal
            new TeamELO("174", 1777), // Athletic Club
            new TeamELO("188", 1734), // Real Sociedad
            new TeamELO("177", 1730), // Espanyol
            new TeamELO("182", 1705), // Málaga
            new TeamELO("180", 1692), // Deportivo de la Coruña
            new TeamELO("184", 1690), // Rayo Vallecano
            new TeamELO("954", 1673), // Elche
            new TeamELO("855", 1658), // Levante
            new TeamELO("953", 1650), // Eibar
            new TeamELO("1450", 1648), // Getafe
            new TeamELO("5683", 1630), // Granada
            new TeamELO("1564", 1592), // Almería
            new TeamELO("952", 1548), // Córdoba
    };

    static OptaEventType[] attackEvents = {
            OptaEventType.PASS_SUCCESSFUL,
            OptaEventType.TAKE_ON,
            OptaEventType.ASSIST,
            OptaEventType.FOUL_RECEIVED,
            OptaEventType.PASS_UNSUCCESSFUL,
            OptaEventType.DISPOSSESSED,
            OptaEventType.ERROR,
            // OptaEventType.DECISIVE_ERROR,        // TODO: Quitado temporalmente por la gravedad del evento
            OptaEventType.ATTEMPT_SAVED,
            OptaEventType.POST,
            OptaEventType.MISS,
            OptaEventType.CAUGHT_OFFSIDE,
            // OptaEventType.OWN_GOAL,              // TODO: Quitado temporalmente por la gravedad del evento
            OptaEventType.GOAL_SCORED_BY_GOALKEEPER,
            OptaEventType.GOAL_SCORED_BY_DEFENDER,
            OptaEventType.GOAL_SCORED_BY_MIDFIELDER,
            OptaEventType.GOAL_SCORED_BY_FORWARD
    };

    static OptaEventType[] foulEvents = {
            OptaEventType.FOUL_COMMITTED,
            OptaEventType.YELLOW_CARD,
            OptaEventType.SECOND_YELLOW_CARD,
            OptaEventType.RED_CARD,
    };

    static Set<OptaEventType> goalEventsSet = new HashSet<OptaEventType>() {{
        add(OptaEventType.OWN_GOAL);
        add(OptaEventType.GOAL_SCORED_BY_GOALKEEPER);
        add(OptaEventType.GOAL_SCORED_BY_DEFENDER);
        add(OptaEventType.GOAL_SCORED_BY_MIDFIELDER);
        add(OptaEventType.GOAL_SCORED_BY_FORWARD);
    }};

    static Set<OptaEventType> changeTeamEventsSet = new HashSet<OptaEventType>() {{
        add(OptaEventType.PASS_UNSUCCESSFUL);
        add(OptaEventType.DISPOSSESSED);
        add(OptaEventType.ERROR);
        add(OptaEventType.DECISIVE_ERROR);
        add(OptaEventType.ATTEMPT_SAVED);
        add(OptaEventType.POST);
        add(OptaEventType.MISS);
        add(OptaEventType.CAUGHT_OFFSIDE);
        add(OptaEventType.GOAL_SCORED_BY_GOALKEEPER);
        add(OptaEventType.GOAL_SCORED_BY_DEFENDER);
        add(OptaEventType.GOAL_SCORED_BY_MIDFIELDER);
        add(OptaEventType.GOAL_SCORED_BY_FORWARD);
    }};

    TemplateMatchEvent templateMatchEvent;
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

    Map<TEAM, Map<TemplateSoccerPlayer, Integer>> foulPriorities = new HashMap<>();
    Map<TEAM, Integer> foulPrioritiesTotal = new HashMap<>();

    TEAM teamWithBall;
    TemplateSoccerPlayer playerWithBall;

    Random rnd = new Random();

    private HashMap<ObjectId, HashMap<String, Integer>> statsCount = new HashMap<>();
}
