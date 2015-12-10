package model;

import model.opta.OptaEventType;
import org.bson.types.ObjectId;
import org.apache.commons.math3.distribution.NormalDistribution;
import play.Logger;

import java.util.*;
import java.util.stream.Collector;
import java.util.stream.Collectors;

public class LiveMatchEventSimulation {
    public int homeScore = 0;
    public int awayScore = 0;
    public HashMap<String, LiveFantasyPoints> liveFantasyPoints = new HashMap<>();

    public LiveMatchEventSimulation(ObjectId aTemplateMatchEventId) {
        long startTime = System.currentTimeMillis();

        TemplateMatchEvent templateMatchEvent = TemplateMatchEvent.findOne(aTemplateMatchEventId);
        templateMatchEventId = templateMatchEvent.templateMatchEventId;
        optaMatchEventId = templateMatchEvent.optaMatchEventId;
        homeTeamId = templateMatchEvent.optaTeamAId;
        awayTeamId = templateMatchEvent.optaTeamBId;
        competitionId = templateMatchEvent.optaCompetitionId;
        seasonId = templateMatchEvent.optaSeasonId;

        pointsTranslationMap = new HashMap<>();

        for (PointsTranslation pointTranslation : PointsTranslation.getAllCurrent()) {
            pointsTranslationMap.put(pointTranslation.eventTypeId, pointTranslation);
        }

        templateSoccerTeamsELO = TemplateSoccerTeam.getTemplateSoccerTeamsELO();

        List<TemplateSoccerPlayer> playersInHome = TemplateSoccerPlayer.findAllFromTemplateTeam(templateMatchEvent.templateSoccerTeamAId);
        for (TemplateSoccerPlayer templateSoccerPlayer : playersInHome) {
            liveFantasyPoints.put(templateSoccerPlayer.templateSoccerPlayerId.toString(), calculateLiveFantasyPoints(templateMatchEvent, templateMatchEvent.templateSoccerTeamAId, templateSoccerPlayer));
        }

        List<TemplateSoccerPlayer> playersInAway = TemplateSoccerPlayer.findAllFromTemplateTeam(templateMatchEvent.templateSoccerTeamBId);
        for (TemplateSoccerPlayer templateSoccerPlayer : playersInAway) {
            liveFantasyPoints.put(templateSoccerPlayer.templateSoccerPlayerId.toString(), calculateLiveFantasyPoints(templateMatchEvent, templateMatchEvent.templateSoccerTeamBId, templateSoccerPlayer));
        }

        Logger.debug("LiveMatchEventSimulation {}: elapsed: {}", aTemplateMatchEventId, System.currentTimeMillis() - startTime);
    }

    private LiveFantasyPoints calculateLiveFantasyPoints(TemplateMatchEvent templateMatchEvent, ObjectId templateSoccerTeamId, TemplateSoccerPlayer templateSoccerPlayer) {
        LiveFantasyPoints liveFantasyPoints = new LiveFantasyPoints();

        if (templateSoccerPlayer.stats.size() > 0) {
            // Logger.debug("LiveFantasyPoints: {} : {}", templateSoccerPlayer.templateSoccerPlayerId.toString(), templateSoccerPlayer.name);

            // Obtener el ELO del oponente
            ObjectId opponentTeamId = templateMatchEvent.templateSoccerTeamAId.equals(templateSoccerTeamId) ? templateMatchEvent.templateSoccerTeamBId : templateMatchEvent.templateSoccerTeamAId;
            int opponentELO = templateSoccerTeamsELO.get(opponentTeamId);

            // Ordenar nuestras estadísticas por comparación al ELO de nuestro contrincante actual
            List<SoccerPlayerStats> statsSorted = sortByELODistance(templateSoccerPlayer.stats, opponentELO);
            for (OptaEventType optaEventType : GenericEventsSet) {
                int estimation = calculateEstimation(statsSorted, optaEventType);
                if (estimation > 0) {
                    // Obtenemos los puntos que vale el evento
                    PointsTranslation pointsTranslation = pointsTranslationMap.get(optaEventType.code);
                    int points = (pointsTranslation != null) ? pointsTranslation.points : 0;

                    LiveEventInfo liveEventInfo = new LiveEventInfo();
                    liveEventInfo.count = estimation;
                    liveEventInfo.points = estimation * points;

                    liveFantasyPoints.events.put(optaEventType.toString(), liveEventInfo);
                    liveFantasyPoints.points += liveEventInfo.points;

                    // Logger.debug("Player: {} Event: {} Estimation: {}", templateSoccerPlayer.name, optaEventType, estimation);
                }
            }
        }

        return liveFantasyPoints;
    }

    final int MAX_ELO_DIFFERENCE = 200;

    private List<SoccerPlayerStats> sortByELODistance(List<SoccerPlayerStats> statsList, int opponentELO) {
        class StatsELO {
            public SoccerPlayerStats stats;
            public int distance;
            public StatsELO(SoccerPlayerStats stats, int distance) {
                this.stats = stats;
                this.distance = distance;
            }
        }

        Comparator<StatsELO> byDistance = (StatsELO o1, StatsELO o2) -> o1.distance - o2.distance;

        List<StatsELO> statsELOList = statsList.stream()
                .map(stats -> new StatsELO(stats, Math.abs(opponentELO - templateSoccerTeamsELO.get(stats.opponentTeamId))))
                .collect(Collectors.toList());

        List<StatsELO> statsFiltered = statsELOList.stream()
                .filter(stats -> stats.distance <= MAX_ELO_DIFFERENCE)
                .collect(Collectors.toList());

        // Logger.debug("Ignoring Matches: {}", statsList.size() - statsFiltered.size());

        // Si nos hemos quedado con pocos partidos de los que obtener estadísticas, ignoramos el filtro y cogemos n partidos
        if (statsFiltered.size() < 3) {
            statsFiltered = statsELOList.stream()
                    .limit(5)
                    .collect(Collectors.toList());
            // Logger.warn("sortByELODistance ---> {}", statsFiltered.size());
        }

        if (statsFiltered.isEmpty()) {
            Logger.error("WTF 9911");
        }

        statsFiltered.sort(byDistance);
        // Logger.debug("[{}]", String.join(", ", statsFiltered.stream().map(statsELO -> String.valueOf(statsELO.distance)).collect(Collectors.toList())));

        return statsFiltered.stream().map(statsELO -> {
            return statsELO.stats;
        }).collect(Collectors.toList());
    }

    private int calculateEstimation(List<SoccerPlayerStats> stats, OptaEventType optaEventType) {
        return estimationUsingMatchRandom(stats, optaEventType);
        // return estimationUsingNormalDistribution(templateSoccerPlayer, optaEventType);
    }

    private int estimationUsingMatchRandom(List<SoccerPlayerStats> stats, OptaEventType optaEventType) {
        int result = 0;
        if (stats.size() > 0) {
            int match = rn.nextInt(stats.size());
            // Logger.debug("TemplateSoccerPlayer: {} Random: {} Size: {}", templateSoccerPlayer.templateSoccerPlayerId, match, templateSoccerPlayer.stats.size());
            SoccerPlayerStats matchStats = stats.get(match);
            if (matchStats.eventsCount != null && matchStats.eventsCount.containsKey(optaEventType.toString())) {
                result = matchStats.eventsCount.get(optaEventType.toString());
            }
        }
        return result;
    }

    private int estimationUsingNormalDistribution(List<SoccerPlayerStats> stats, OptaEventType optaEventType) {
        int min = Integer.MAX_VALUE;
        int max = Integer.MIN_VALUE;
        float numEvents = 0;
        int numMatches = 0;
        for (SoccerPlayerStats stat : stats) {
            if (stat.eventsCount != null && stat.playedMinutes > 0) {
                int count = stat.eventsCount.containsKey(optaEventType.toString()) ? stat.eventsCount.get(optaEventType.toString()) : 0;
                if (count > max) {
                    max = count;
                }
                if (count < min) {
                    min = count;
                }
                float countNormalized = count * 90 / stat.playedMinutes;
                numEvents += countNormalized;
                numMatches++;
            }
        }

        int result = 0;

        if (numEvents > 0) {
            float average = numEvents / numMatches;
            double stdev = calculateStDev(stats, optaEventType, average);

            // NORM.INV
            NormalDistribution normalDistribution = new NormalDistribution(average, stdev);
            double value = normalDistribution.inverseCumulativeProbability(Math.random());

            value = Math.round(value);
            value = Math.min(value, max);
            value = Math.max(value, min);

            result = (int) value;

            if (result > 0) {
                Logger.debug("Matches: {} Event: {} * {} Min: {} Max: {}, Average: {} StDev: {} Norm.Inv: {} => {}",
                        numMatches, optaEventType, numEvents, min, max, average, stdev, value, result);
            }
        }

        return result;
    }

    private double calculateStDev(List<SoccerPlayerStats> stats, OptaEventType optaEventType, float average) {
        float sum = 0;
        int numMatches = 0;
        for (SoccerPlayerStats stat : stats) {
            if (stat.eventsCount != null && stat.playedMinutes > 0) {
                float count = stat.eventsCount.containsKey(optaEventType.toString()) ? stat.eventsCount.get(optaEventType.toString()) : 0;
                float countNormalized = count * 90 / stat.playedMinutes;

                sum += Math.pow(countNormalized - average, 2);
                numMatches++;
            }
        }
        if (numMatches > 1) {
            sum = sum / (numMatches - 1);
        }
        return Math.sqrt(sum);
    }

    Random rn = new Random();

    ObjectId templateMatchEventId;
    String optaMatchEventId;
    String homeTeamId;
    String awayTeamId;
    String competitionId;
    String seasonId;

    HashMap<Integer, PointsTranslation> pointsTranslationMap;
    Map<ObjectId, Integer> templateSoccerTeamsELO;

    public static Set<OptaEventType> GenericEventsSet = new HashSet<OptaEventType>() {{
        add(OptaEventType.PASS_SUCCESSFUL);
        add(OptaEventType.PASS_UNSUCCESSFUL);
        add(OptaEventType.TAKE_ON);
        add(OptaEventType.FOUL_RECEIVED);
        add(OptaEventType.TACKLE);
        add(OptaEventType.INTERCEPTION);
        add(OptaEventType.SAVE_GOALKEEPER);
        add(OptaEventType.CLAIM);
        add(OptaEventType.CLEARANCE);
        add(OptaEventType.MISS);
        add(OptaEventType.POST);
        add(OptaEventType.ATTEMPT_SAVED);
        add(OptaEventType.YELLOW_CARD);
        add(OptaEventType.PUNCH);
        add(OptaEventType.DISPOSSESSED);
        add(OptaEventType.ERROR);
        add(OptaEventType.DECISIVE_ERROR);
        add(OptaEventType.SAVE_PLAYER);
        add(OptaEventType.ASSIST);
        add(OptaEventType.TACKLE_EFFECTIVE);
        add(OptaEventType.GOAL_SCORED_BY_GOALKEEPER);
        add(OptaEventType.GOAL_SCORED_BY_DEFENDER);
        add(OptaEventType.GOAL_SCORED_BY_MIDFIELDER);
        add(OptaEventType.GOAL_SCORED_BY_FORWARD);
        add(OptaEventType.OWN_GOAL);
        add(OptaEventType.FOUL_COMMITTED);
        add(OptaEventType.SECOND_YELLOW_CARD);
        add(OptaEventType.RED_CARD);
        add(OptaEventType.CAUGHT_OFFSIDE);
        add(OptaEventType.PENALTY_COMMITTED);
        add(OptaEventType.PENALTY_FAILED);
        add(OptaEventType.GOALKEEPER_SAVES_PENALTY);
    }};
}

