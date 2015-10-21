package model;

import model.opta.OptaEvent;
import model.opta.OptaEventType;
import org.bson.types.ObjectId;
import play.Logger;
import utils.ListUtils;

import java.util.*;
import java.util.stream.Collectors;

public class MatchEventSimulation {
    final int TICK_SECONDS = 5;

    public int homeScore = 0;
    public int awayScore = 0;
    public ArrayList<SimulationEvent> simulationEvents = new ArrayList<>();

    public MatchEventSimulation(ObjectId aTemplateMatchEventId) {
        long startTime = System.currentTimeMillis();

        TemplateMatchEvent templateMatchEvent = TemplateMatchEvent.findOne(aTemplateMatchEventId);
        templateMatchEventId = templateMatchEvent.templateMatchEventId;
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

        stats = StatsSimulation.instance(templateMatchEvent);

        startGame();
        runGame();

        Logger.debug("MatchEventSimulation {}: elapsed: {}", aTemplateMatchEventId, System.currentTimeMillis() - startTime);
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
            playerWithBall = stats.getTarget(teamWithBall);
        }

        // Qué acción realiza
        OptaEventType action = stats.selectAction(playerWithBall, StatsSimulation.AttackEvents, OptaEventType.PASS_UNSUCCESSFUL);
        createEvent(action, playerWithBall);

        // Gol?
        if (StatsSimulation.GoalEventsSet.contains(action)) {
            if (action == OptaEventType.OWN_GOAL) {
                if (teamWithBall.equals(StatsSimulation.TEAM.HOME))
                    awayScore++;
                else
                    homeScore++;
            }
            else {
                if (teamWithBall.equals(StatsSimulation.TEAM.HOME))
                    homeScore++;
                else
                    awayScore++;
            }
            // OptaMatchEventStats.updateMatchResult(optaMatchEventId, homeScore, awayScore);
        }

        // Se ha cometido una falta?
        if (action.equals(OptaEventType.FOUL_RECEIVED)) {
            ObjectId foulPlayer = stats.getTargetThatFoulCommited(teamWithoutTeam());
            if (foulPlayer != null) {
                OptaEventType foulAction = stats.selectAction(foulPlayer, StatsSimulation.FoulEvents, OptaEventType._INVALID_);
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
        if (StatsSimulation.ChangeTeamEventsSet.contains(action)) {
            teamWithBall = changeTeam();

            // Decidir quién ha cogido el balón
            playerWithBall = stats.getTarget(teamWithBall);

            OptaEventType[] defenseEvents = stats.getDefenseEventsFromAttackEvent(action);
            if (defenseEvents.length > 0) {
                // Generamos el evento por el que se quitó el balón
                OptaEventType defenseAction = stats.selectAction(playerWithBall, defenseEvents, OptaEventType._INVALID_);
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
                playerWithBall = stats.getTarget(teamWithBall);
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

    StatsSimulation.TEAM teamBegins() {
        return StatsSimulation.TEAM.HOME;
    }

    StatsSimulation.TEAM changeTeam() {
        return teamWithBall.equals(StatsSimulation.TEAM.HOME) ? StatsSimulation.TEAM.AWAY : StatsSimulation.TEAM.HOME;
    }
    StatsSimulation.TEAM teamWithoutTeam () {
        return changeTeam();
    }


    int getStat(HashMap<String, Integer> stats, OptaEventType optaEventType) {
        return stats.containsKey(optaEventType.toString()) ? stats.get(optaEventType.toString()) : 0;
    }

    void createEvent(OptaEventType eventType, ObjectId templateSoccerPlayerId) {
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
        simulationEvent.templateSoccerPlayerId = templateSoccerPlayerId;
        simulationEvent.eventType = eventType;
        simulationEvent.points = points;
        simulationEvents.add(simulationEvent);
    }

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

    HashMap<Integer, PointsTranslation> pointsTranslationMap;

    StatsSimulation.TEAM teamWithBall;
    ObjectId playerWithBall;

    private StatsSimulation stats;
}
