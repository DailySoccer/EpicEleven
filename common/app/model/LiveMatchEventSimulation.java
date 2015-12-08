package model;

import model.opta.OptaEventType;
import org.bson.types.ObjectId;
import play.Logger;

import java.util.*;

public class LiveMatchEventSimulation {
    final int TICK_SECONDS = 5;

    public int homeScore = 0;
    public int awayScore = 0;
    public ArrayList<SimulationEvent> simulationEvents = new ArrayList<>();
    HashMap<String, LiveFantasyPoints> liveFantasyPoints = new HashMap<>();

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
            calculateLiveFantasyPoints(templateSoccerPlayer);
        }

        List<TemplateSoccerPlayer> playersInAway = TemplateSoccerPlayer.findAllFromTemplateTeam(templateMatchEvent.templateSoccerTeamAId);
        for (TemplateSoccerPlayer templateSoccerPlayer : playersInAway) {
            calculateLiveFantasyPoints(templateSoccerPlayer);
        }

        Logger.debug("LiveMatchEventSimulation {}: elapsed: {}", aTemplateMatchEventId, System.currentTimeMillis() - startTime);
    }

    private LiveFantasyPoints calculateLiveFantasyPoints(TemplateSoccerPlayer templateSoccerPlayer) {
        LiveFantasyPoints liveFantasyPoints = new LiveFantasyPoints();

        for (OptaEventType optaEventType : OptaEventType.values()) {
            int estimation = calculateEstimation(templateSoccerPlayer, optaEventType);
            if (estimation > 0) {
                // Obtenemos los puntos que vale el evento
                PointsTranslation pointsTranslation = pointsTranslationMap.get(optaEventType.code);
                int points = (pointsTranslation != null) ? pointsTranslation.points : 0;

                LiveEventInfo liveEventInfo = new LiveEventInfo();
                liveEventInfo.count = estimation;
                liveEventInfo.points = estimation * points;

                liveFantasyPoints.events.put(optaMatchEventId.toString(), liveEventInfo);
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

            Logger.debug("Player: {} Matches: {} Event: {} * {} Min: {} Max: {}, Average: {} StDev: {}",
                    templateSoccerPlayer.name, numMatches, optaEventType, numEvents, min, max, average, stdev);

            double base = average - stdev;
            double value = base + (stdev * 2) * Math.random();
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
}

