package actors;

import akka.actor.UntypedActor;
import model.*;
import model.opta.OptaEvent;
import model.opta.OptaEventType;
import model.opta.OptaMatchEventStats;
import org.bson.types.ObjectId;
import play.Logger;

import java.util.*;
import java.util.stream.Collectors;

public class MatchEventActor extends UntypedActor {
    enum TEAM {
        HOME,
        AWAY,
        MAX
    };

    final String PLAYED_MATCHES = "PLAYED_MATCHES";
    final String PLAYED_MINUTES = "PLAYED_MINUTES";
    final int TICK_SECONDS = 5;

    public MatchEventActor(ObjectId aTemplateMatchEventId) {
        long startTime = System.currentTimeMillis();

        templateMatchEventId = aTemplateMatchEventId;

        Logger.debug("MatchActor {}: init", templateMatchEventId);

        TemplateMatchEvent templateMatchEvent = TemplateMatchEvent.findOne(templateMatchEventId);
        optaMatchEventId = templateMatchEvent.optaMatchEventId;
        homeTeamId = templateMatchEvent.optaTeamAId;
        awayTeamId = templateMatchEvent.optaTeamBId;
        competitionId = templateMatchEvent.optaCompetitionId;
        seasonId = templateMatchEvent.optaSeasonId;

        OptaEvent optaEvent = OptaEvent.findLast(optaMatchEventId);
        if (optaEvent != null) {
            timestamp = optaEvent.timestamp;
            periodId = optaEvent.periodId;
            min = optaEvent.min;
            sec = optaEvent.sec;
        }
        else {
            timestamp = GlobalDate.getCurrentDate();
            periodId = 0;
            min = 0;
            sec = 0;
        }

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
        prioritiesTotal.put(TEAM.HOME, 0);
        priorities.get(TEAM.HOME).forEach((key, priority) -> {
            prioritiesTotal.put(TEAM.HOME, prioritiesTotal.get(TEAM.HOME) + priority);
        });

        priorities.put(TEAM.AWAY, calculatePriorities(lineUp.get(TEAM.AWAY)));
        prioritiesTotal.put(TEAM.AWAY, 0);
        priorities.get(TEAM.AWAY).forEach((key, priority) -> {
            prioritiesTotal.put(TEAM.AWAY, prioritiesTotal.get(TEAM.AWAY) + priority);
        });

        templateMatchEvent.setGameSimulated();

        startGame();
        onTick();

        Logger.debug("elapsed: {}", System.currentTimeMillis() - startTime);
    }

    @Override
    public void onReceive(Object msg) {
        try {
            if (msg instanceof String) {
                switch ((String) msg) {
                    case "Tick":
                        onTick();
                        break;
                }
            }
        }
        catch (Exception exc) {
            // Logger.info("Timeout 1026, probablemente el servidor esta saturado...");
        }
    }

    void onTick() {
        Logger.debug("Tick {}", templateMatchEventId);

        for (int p=0; p<2; p++) {
            if (p == 1) {
                secondHalfGame();
            }
            for (int m = 0; m < 45; m++) {
                for (int s = 0; s < 60; s += TICK_SECONDS) {
                    min = 45 * p + m;
                    sec = s;
                    onGameTick();
                }
            }
        }

        stopGame();
        OptaMatchEventStats.updateMatchResult(optaMatchEventId, homeScore, awayScore);

        new OptaMatchEventChangeProcessor(optaMatchEventId).process();
    }

    void onGameTick() {
        if (teamWithBall == null) {
            teamWithBall = teamBegins();
        }

        // Quién tiene el balón
        playerWithBall = getTarget();

        // Qué acción realiza
        String optaTeam = teamWithBall.equals(TEAM.HOME) ? homeTeamId : awayTeamId;
        OptaEventType action = selectAction(playerWithBall);

        createEvent(action.code, optaTeam, playerWithBall.optaPlayerId).insert().markAsSimulated();

        // Gol?
        if (goalEvents.contains(action)) {
            if (teamWithBall.equals(TEAM.HOME))
                homeScore++;
            else
                awayScore++;
            OptaMatchEventStats.updateMatchResult(optaMatchEventId, homeScore, awayScore);
        }

        // Pérdida de balón?
        if (changeTeamEvents.contains(action)) {
            teamWithBall = changeTeam();
        }
    }

    void startGame() {
        periodId = 1;   // START_GAME
        createEvent(OptaEventType.PERIOD_BEGINS.code).insert();
    }

    void secondHalfGame() {
        periodId = 2;
        createEvent(OptaEventType.PERIOD_BEGINS.code).insert();
    }

    void stopGame() {
        periodId = 14;  // POST_GAME;
        createEvent(OptaEventType.GAME_END.code).insert();

    }

    TEAM teamBegins() {
        return TEAM.HOME;
    }

    TEAM changeTeam() {
        return teamWithBall.equals(TEAM.HOME) ? TEAM.AWAY : TEAM.HOME;
    }

    TemplateSoccerPlayer getTarget() {
        TemplateSoccerPlayer target = null;
        int die = rnd.nextInt(prioritiesTotal.get(teamWithBall));
        for (Map.Entry<TemplateSoccerPlayer, Integer> entry : priorities.get(teamWithBall).entrySet()) {
            die -= entry.getValue();
            if (die < 0) {
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

        int die = rnd.nextInt(total);
        for (OptaEventType optaEventType : attackEvents) {
            die -= getStat(stats, optaEventType);
            if (die < 0) {
                action = optaEventType;
                break;
            }
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
        for (TemplateSoccerPlayer player : players) {
            Logger.info("{}: {}%", player.name, priorities.get(player)*100/total);
        }
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

        for (OptaEventType optaEventType : OptaEventType.values()){
            int count = (int) Model.optaEvents().count("{optaPlayerId: #, typeId: #, simulated: {$exists: false}}",
                    player.optaPlayerId, optaEventType.code);
            if (count > 0) {
                stats.put(optaEventType.toString(), count);
                info.append(optaEventType).append(" : ").append(count).append(" \n");
            }
        }

        Logger.info(info.toString());
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

    OptaEvent createEvent(int typeId) {
        OptaEvent optaEvent = new OptaEvent();
        optaEvent.gameId = optaMatchEventId;
        optaEvent.homeTeamId = homeTeamId;
        optaEvent.awayTeamId = awayTeamId;
        optaEvent.competitionId = competitionId;
        optaEvent.seasonId = seasonId;

        optaEvent.periodId = periodId;
        optaEvent.timestamp = timestamp;
        optaEvent.min = min;
        optaEvent.sec = sec;

        optaEvent.eventId = eventId++;
        optaEvent.typeId = typeId;
        return optaEvent;
    }

    OptaEvent createEvent(int typeId, String teamId, String optaPlayerId) {
        OptaEvent optaEvent = createEvent(typeId);
        optaEvent.teamId = Integer.parseInt(teamId);
        optaEvent.optaPlayerId = optaPlayerId;

        PointsTranslation pointsTranslation = pointsTranslationMap.get(typeId);
        optaEvent.points = (pointsTranslation != null) ? pointsTranslation.points : 0;
        optaEvent.pointsTranslationId = (pointsTranslation != null) ? pointsTranslation.pointsTranslationId : null;
        return optaEvent;
    }

    static OptaEventType[] attackEvents = {
            OptaEventType.PASS_SUCCESSFUL, OptaEventType.TAKE_ON, OptaEventType.ASSIST, OptaEventType.FOUL_RECEIVED,
            OptaEventType.PASS_UNSUCCESSFUL, OptaEventType.DISPOSSESSED, OptaEventType.ERROR, OptaEventType.DECISIVE_ERROR,
            OptaEventType.ATTEMPT_SAVED, OptaEventType.POST, OptaEventType.MISS, OptaEventType.OWN_GOAL,
            OptaEventType.CAUGHT_OFFSIDE, OptaEventType.GOAL_SCORED_BY_GOALKEEPER, OptaEventType.GOAL_SCORED_BY_DEFENDER,
            OptaEventType.GOAL_SCORED_BY_MIDFIELDER, OptaEventType.GOAL_SCORED_BY_FORWARD
    };

    Set<OptaEventType> goalEvents = new HashSet<OptaEventType>() {{
        add(OptaEventType.GOAL_SCORED_BY_GOALKEEPER);
        add(OptaEventType.GOAL_SCORED_BY_DEFENDER);
        add(OptaEventType.GOAL_SCORED_BY_MIDFIELDER);
        add(OptaEventType.GOAL_SCORED_BY_FORWARD);
    }};

    Set<OptaEventType> changeTeamEvents = new HashSet<OptaEventType>() {{
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

    int homeScore = 0;
    int awayScore = 0;

    Random rnd = new Random();

    private HashMap<ObjectId, HashMap<String, Integer>> statsCount = new HashMap<>();
}

/*
Acciones CON BALON:

PASS_SUCCESSFUL         (1001, "Any pass successful from one player to another."),
TAKE_ON                 (3, "Attempted dribble past an opponent" ),
ASSIST                  (1210, "The pass was an assist for a shot"),
FOUL_RECEIVED           (4, "Player who was fouled"),

Acciones FINALES CON BALON:

PASS_UNSUCCESSFUL       (1002, "Any pass attempted from one player to a wrong place."),

DISPOSSESSED            (50, "Player is successfully tacked and loses possession of the ball"),
ERROR                   (51, "Mistake by player losing the ball"),
DECISIVE_ERROR          (1051, "Mistake by player ending in a conceded goal"),

ATTEMPT_SAVED           (15, "Shot saved, event for the player who shot the ball"),
POST                    (14, "The ball hits the frame of the goal"),
MISS                    (13, "Shot on goal which goes wide over the goal"),

OWN_GOAL                (1699, "Own goal scored by the player"),
CAUGHT_OFFSIDE          (1072, "Player who is offside"),
GOAL_SCORED_BY_GOALKEEPER   (1601, "Goal scored by the goalkeeper"),
GOAL_SCORED_BY_DEFENDER     (1602, "Goal scored by a defender"),
GOAL_SCORED_BY_MIDFIELDER   (1603, "Goal scored by a midfielder"),
GOAL_SCORED_BY_FORWARD      (1604, "Goal scored by a forward"),

Acciones CONTRARIO:
CLEARANCE               (12, "Player under pressure hits ball clear of the defensive zone or/and out of play"),
TACKLE                  (7, "Tackle: dispossesses an opponent of the ball, not retaining possession"),
TACKLE_EFFECTIVE        (1007, "Tackle: dispossesses an opponent of the ball, retaining possession"),
INTERCEPTION            (8, "When a player intercepts any pass event between opposition players and prevents the ball reaching its target"),

SAVE_PLAYER             (1010, "Player blockes a shot."),
FOUL_COMMITTED          (1004, "Player who committed the foul"),

SAVE_GOALKEEPER         (10, "Goalkeeper saves a shot on goal."),
CLAIM                   (11, "Goalkeeper catches a crossed ball"),
PUNCH                   (41, "Ball is punched clear by Goalkeeper"),
 */

/*
//CAUGHT_OFFSIDE          (72, "Player who is offside"),

YELLOW_CARD             (17, "Yellow card shown to player"),
SECOND_YELLOW_CARD      (1017, "Second yellow card shown to player"),
RED_CARD                (1117, "Red card shown to player"),

PENALTY_COMMITTED       (1409, "Player who committed the foul (penalty)"),
PENALTY_FAILED          (1410, "Player who shots penalty and fails"),
GOALKEEPER_SAVES_PENALTY(1458, "Goalkeeper saves a penalty shot"),

PERIOD_ENDS             (30, "Period or Match ends"),
PERIOD_BEGINS           (32, "Period or Match begins"),
CLEAN_SHEET             (2000, "Clean sheet: More than 60 min played without conceding any goal"),
GOAL_CONCEDED           (2001, "Goal conceded while player is on the field"),
GAME_END                (9998, "Game has finished for us"),
 */


/*
public OptaEvent(Element event, Element game) {
        this.gameId = game.getAttributeValue("id");
        this.homeTeamId = game.getAttributeValue("home_team_id");
        this.awayTeamId = game.getAttributeValue("away_team_id");
        this.competitionId = game.getAttributeValue("competition_id");
        this.seasonId = game.getAttributeValue("season_id");
        this.teamId = Integer.parseInt(event.getAttributeValue("team_id"));
        this.periodId = Integer.parseInt(event.getAttributeValue("period_id"));
        this.eventId = Integer.parseInt(event.getAttributeValue("event_id"));
        this.typeId = Integer.parseInt(event.getAttributeValue("type_id"));
        int outcome = Integer.parseInt(event.getAttributeValue("outcome"));
        this.timestamp = GlobalDate.parseDate(event.getAttributeValue("timestamp"), null);
        this.lastModified = GlobalDate.parseDate(event.getAttributeValue("last_modified"), null);
        this.min = Integer.parseInt(event.getAttributeValue("min"));
        this.sec = Integer.parseInt(event.getAttributeValue("sec"));

        ArrayList<Integer> qualifiers = new ArrayList<>();

        if (event.getAttribute("player_id") != null) {
            this.optaPlayerId = event.getAttributeValue("player_id");
        }

        String optaPlayerOffsideId = "<player_offside>";
        if (event.getChildren("Q") != null) {
            List<Element> qualifierList = event.getChildren("Q");
            qualifiers = new ArrayList<>((qualifierList).size());
            for (Element qualifier : qualifierList) {
                Integer tempQualifier = Integer.parseInt(qualifier.getAttributeValue("qualifier_id"));
                qualifiers.add(tempQualifier);

                // Se ha dejado a un futbolista en fuera de juego?
                if (tempQualifier == 7) {
                    optaPlayerOffsideId = qualifier.getAttributeValue("value");
                    // Logger.info("optaOtherPlayerId: {}", optaPlayerOffsideId);
                }
            }
        }
        // DERIVED EVENTS GO HERE
        // Pase exitoso o fracasado
        if (this.typeId == 1) {
            if (outcome == 1) {
                this.typeId = OptaEventType.PASS_SUCCESSFUL.code;  //Pase exitoso-> 1001
            }
            else {
                this.typeId = OptaEventType.PASS_UNSUCCESSFUL.code;  //Pase fracasado -> 1002
            }
        }
        // Asistencia
        if (this.typeId == OptaEventType.PASS_SUCCESSFUL.code && qualifiers.contains(210)) {
            this.typeId = OptaEventType.ASSIST.code;  //Asistencia -> 1210
        }
        // Falta/Penalty infligido
        else if (this.typeId == OptaEventType.FOUL_RECEIVED.code && outcome == 0) {
            if (qualifiers.contains(9)) {
                this.typeId = OptaEventType.PENALTY_COMMITTED.code;  //Penalty infligido -> 1409
            } else {
                this.typeId = OptaEventType.FOUL_COMMITTED.code;  // Falta infligida -> 1004
            }
        }
        // Segunda tarjeta amarilla -> 1017
        else if (this.typeId == OptaEventType.YELLOW_CARD.code && qualifiers.contains(32)) {
            this.typeId = OptaEventType.SECOND_YELLOW_CARD.code;
        }
        // Tarjeta roja -> 1117
        else if (this.typeId == OptaEventType.YELLOW_CARD.code && qualifiers.contains(33)) {
            this.typeId = OptaEventType.RED_CARD.code;
        }
        // Penalty miss -> 1410
        else if ((this.typeId == OptaEventType.MISS.code || this.typeId == OptaEventType.POST.code ||
                this.typeId == OptaEventType.ATTEMPT_SAVED.code) &&
                outcome == 0 && qualifiers.contains(9)) {
            this.typeId = OptaEventType.PENALTY_FAILED.code;
        } else if (this.typeId == 16 && outcome == 1) {
            // Gol en contra -> 1699
            if (qualifiers.contains(28)) {
                this.typeId = OptaEventType.OWN_GOAL.code;
            } else {
                // Diferencias en goles:
                try {
                    OptaPlayer scorer = Model.optaPlayers().findOne("{optaPlayerId: #}", this.optaPlayerId).as(OptaPlayer.class);
                    if (scorer.position.equals("Goalkeeper")) {
                        // Gol del portero
                        this.typeId = OptaEventType.GOAL_SCORED_BY_GOALKEEPER.code;
                    } else if (scorer.position.equals("Defender")) {
                        // Gol del defensa
                        this.typeId = OptaEventType.GOAL_SCORED_BY_DEFENDER.code;
                    } else if (scorer.position.equals("Midfielder")) {
                        // Gol del medio
                        this.typeId = OptaEventType.GOAL_SCORED_BY_MIDFIELDER.code;
                    } else if (scorer.position.equals("Forward")) {
                        // Gol del delantero
                        this.typeId = OptaEventType.GOAL_SCORED_BY_FORWARD.code;
                    }
                } catch (NullPointerException e) {
                    Logger.info("Player not found: " + this.optaPlayerId);
                }
            }
        }
        // Penalty parado -> 1058
        else if (this.typeId == 58 && !qualifiers.contains(186)) {
            this.typeId = OptaEventType.GOALKEEPER_SAVES_PENALTY.code;
        }
        // Effective Tackle -> 1007
        else if (this.typeId == OptaEventType.TACKLE.code && outcome == 1) {
            this.typeId = OptaEventType.TACKLE_EFFECTIVE.code;
        }
        // Caught Offside -> 1072
        else if (this.typeId == 2 && qualifiers.contains(7)) {
            this.typeId = OptaEventType.CAUGHT_OFFSIDE.code;
            this.optaPlayerId = optaPlayerOffsideId;
        }
        // Player Saves -> 1010
        else if (this.typeId == OptaEventType.SAVE_GOALKEEPER.code && qualifiers.contains(94)) {
            this.typeId = OptaEventType.SAVE_PLAYER.code;
        }
        // Player Saves -> 1051
        else if (this.typeId == OptaEventType.ERROR.code) {
            if (qualifiers.contains(170)) {
                this.typeId = OptaEventType.DECISIVE_ERROR.code;
            }
            else if (!qualifiers.contains(169)) {
                this.typeId = OptaEventType._INVALID_.code;
            }
        }

        // Si no es un borrado, poner a INVALID si no está entre los que nos interesan
        if (this.typeId != 43) {
            this.typeId = OptaEventType.getEnum(this.typeId).code;
        }
    }
 */