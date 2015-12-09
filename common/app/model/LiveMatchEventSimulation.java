package model;

import model.opta.OptaEventType;
import org.bson.types.ObjectId;
import org.apache.commons.math3.distribution.NormalDistribution;
import play.Logger;

import java.util.*;

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

        List<TemplateSoccerPlayer> playersInHome = TemplateSoccerPlayer.findAllFromTemplateTeam(templateMatchEvent.templateSoccerTeamAId);
        for (TemplateSoccerPlayer templateSoccerPlayer : playersInHome) {
            liveFantasyPoints.put(templateSoccerPlayer.templateSoccerPlayerId.toString(), calculateLiveFantasyPoints(templateSoccerPlayer));
        }

        List<TemplateSoccerPlayer> playersInAway = TemplateSoccerPlayer.findAllFromTemplateTeam(templateMatchEvent.templateSoccerTeamBId);
        for (TemplateSoccerPlayer templateSoccerPlayer : playersInAway) {
            liveFantasyPoints.put(templateSoccerPlayer.templateSoccerPlayerId.toString(), calculateLiveFantasyPoints(templateSoccerPlayer));
        }

        Logger.debug("LiveMatchEventSimulation {}: elapsed: {}", aTemplateMatchEventId, System.currentTimeMillis() - startTime);
    }

    private LiveFantasyPoints calculateLiveFantasyPoints(TemplateSoccerPlayer templateSoccerPlayer) {
        LiveFantasyPoints liveFantasyPoints = new LiveFantasyPoints();

        for (OptaEventType optaEventType : GenericEventsSet) {
            int estimation = calculateEstimation(templateSoccerPlayer, optaEventType);
            if (estimation > 0) {
                // Obtenemos los puntos que vale el evento
                PointsTranslation pointsTranslation = pointsTranslationMap.get(optaEventType.code);
                int points = (pointsTranslation != null) ? pointsTranslation.points : 0;

                LiveEventInfo liveEventInfo = new LiveEventInfo();
                liveEventInfo.count = estimation;
                liveEventInfo.points = estimation * points;

                liveFantasyPoints.events.put(optaEventType.toString(), liveEventInfo);
                liveFantasyPoints.points += liveEventInfo.points;

                Logger.debug("Player: {} Event: {} Estimation: {}", templateSoccerPlayer.name, optaEventType, estimation);
            }
        }

        return liveFantasyPoints;
    }

    private int calculateEstimation(TemplateSoccerPlayer templateSoccerPlayer, OptaEventType optaEventType) {
        int min = Integer.MAX_VALUE;
        int max = Integer.MIN_VALUE;
        float numEvents = 0;
        int numMatches = 0;
        for (SoccerPlayerStats stat : templateSoccerPlayer.stats) {
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
            double stdev = calculateStDev(templateSoccerPlayer, optaEventType, average);

            // NORM.INV
            NormalDistribution normalDistribution = new NormalDistribution(average, stdev);
            double value = normalDistribution.inverseCumulativeProbability(Math.random());

            Logger.debug("Player: {} Matches: {} Event: {} * {} Min: {} Max: {}, Average: {} StDev: {} Norm.Inv: {}",
                    templateSoccerPlayer.name, numMatches, optaEventType, numEvents, min, max, average, stdev, value);

            value = Math.round(value);
            value = Math.min(value, max);
            value = Math.max(value, min);

            result = (int) value;
        }

        return result;
    }

    private double calculateStDev(TemplateSoccerPlayer templateSoccerPlayer, OptaEventType optaEventType, float average) {
        float sum = 0;
        int numMatches = 0;
        for (SoccerPlayerStats stat : templateSoccerPlayer.stats) {
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

    public ObjectId templateMatchEventId;
    public String optaMatchEventId;
    public String homeTeamId;
    public String awayTeamId;
    public String competitionId;
    public String seasonId;

    HashMap<Integer, PointsTranslation> pointsTranslationMap;

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

