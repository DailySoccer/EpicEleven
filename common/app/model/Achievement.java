package model;

import org.joda.money.Money;
import play.Logger;
import utils.MoneyUtils;
import org.bson.types.ObjectId;

import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;
import com.google.common.collect.ImmutableList;

public class Achievement {
    // Los fantasyPoints están expresados como "coma fija" (con un decimal)
    static public int FantasyPoints(int points) {
        return points * 10;
    }

    static public void trueSkillChanged(User user, Contest contest) {

        //
        // TRUE_SKILL_N
        //
        evaluateAchievements(user, user.trueSkill, new HashMap<AchievementType, Integer>() {{
            put(AchievementType.TRUE_SKILL_500, 500);
            put(AchievementType.TRUE_SKILL_1000, 1000);
            put(AchievementType.TRUE_SKILL_2000, 2000);
            put(AchievementType.TRUE_SKILL_3000, 3000);
            put(AchievementType.TRUE_SKILL_4000, 4000);
        }});

        Logger.debug("trueSkillChanged {}: User: {}: Contest: {}",
                contest.simulation ? "Simulation" : "Official",
                user.firstName + " " + user.lastName,
                contest.name);
    }

    static public void playedContest(Contest contest) {
        for (ContestEntry contestEntry : contest.contestEntries) {
            playedContest(contestEntry, contest);
        }

        if (!contest.simulation) {
            playedSoccerPlayers(contest);
        }
    }

    static private void playedContest(ContestEntry contestEntry, Contest contest) {
        User user = User.findOne(contestEntry.userId);

        // Ha ganado?
        if (contestEntry.position == 0) {
            // Achievements por Ganar
            if (contest.simulation) {
                wonSimulationContest(user, contestEntry, contest);
            }
            else {
                wonOfficialContest(user, contestEntry, contest);
            }
        }

        // Achievements por Participar
        if (contest.simulation) {
            playedSimulationContest(user, contestEntry, contest);
        }
        else {
            playedOfficialContest(user, contestEntry, contest);
        }

        // Recibió un premio?
        if (contestEntry.prize.isPositive()) {
            wonPrize(user, contestEntry, contest);
        }

        Logger.debug("playedContest {}: User: {}: Contest: {} Position: {}",
                contest.simulation ? "Simulation" : "Official",
                user.firstName + " " + user.lastName,
                contest.name,
                contestEntry.position);
    }

    static private long wonSimulationContest(User user, ContestEntry contestEntry, Contest contest) {
        long won = Contest.countWonSimulations(user.userId);

        //
        // WON_N_VIRTUAL_CONTEST
        //
        evaluateAchievements(user, won, new HashMap<AchievementType, Integer>() {{
            put(AchievementType.WON_1_VIRTUAL_CONTEST, 1);
            put(AchievementType.WON_10_VIRTUAL_CONTESTS, 10);
        }});

        //
        // FP_N_VIRTUAL_CONTEST
        //
        evaluateAchievements(user, contestEntry.fantasyPoints, new HashMap<AchievementType, Integer>() {{
            put(AchievementType.FP_1000_VIRTUAL_CONTEST, FantasyPoints(1000));
        }});

        //
        // DIFF_FP_N_VIRTUAL_CONTEST
        //
        ContestEntry second = contest.getContestEntryInPosition(1);
        long diffFP = contestEntry.fantasyPoints - second.fantasyPoints;

        evaluateAchievements(user, diffFP, new HashMap<AchievementType, Integer>() {{
            put(AchievementType.DIFF_FP_200_VIRTUAL_CONTEST, FantasyPoints(200));
        }});

        return won;
    }

    static private long playedSimulationContest(User user, ContestEntry contestEntry, Contest contest) {
        long played = Contest.countPlayedSimulations(user.userId);

        //
        // PLAYED_N_VIRTUAL_CONTESTS
        //
        evaluateAchievements(user, played, new HashMap<AchievementType, Integer>() {{
            put(AchievementType.PLAYED_10_VIRTUAL_CONTESTS, 10);
        }});

        return played;
    }

    static private long wonOfficialContest(User user, ContestEntry contestEntry, Contest contest) {
        long won = Contest.countWonOfficial(user.userId);

        //
        // WON_N_OFFICIAL_CONTEST
        //
        evaluateAchievements(user, won, new HashMap<AchievementType, Integer>() {{
            put(AchievementType.WON_1_OFFICIAL_CONTEST, 1);
            put(AchievementType.WON_10_OFFICIAL_CONTESTS, 10);
        }});

        //
        // FP_N_OFFICIAL_CONTEST
        //
        evaluateAchievements(user, contestEntry.fantasyPoints, new HashMap<AchievementType, Integer>() {{
            put(AchievementType.FP_1000_OFFICIAL_CONTEST, FantasyPoints(1000));
        }});

        //
        // DIFF_FP_N_OFFICIAL_CONTEST
        //
        ContestEntry second = contest.getContestEntryInPosition(1);
        long diffFP = contestEntry.fantasyPoints - second.fantasyPoints;

        evaluateAchievements(user, diffFP, new HashMap<AchievementType, Integer>() {{
            put(AchievementType.DIFF_FP_200_OFFICIAL_CONTEST, FantasyPoints(200));
        }});

        return won;
    }

    static private long playedOfficialContest(User user, ContestEntry contestEntry, Contest contest) {
        long played = Contest.countPlayedOfficial(user.userId);

        //
        // PLAYED_N_OFFICIAL_CONTESTS
        //
        evaluateAchievements(user, played, new HashMap<AchievementType, Integer>() {{
            put(AchievementType.PLAYED_10_OFFICIAL_CONTESTS, 10);
        }});

        return played;
    }

    static private void wonPrize(User user, ContestEntry contestEntry, Contest contest) {
        // Ganó manager points?
        if (contestEntry.prize.getCurrencyUnit().equals(MoneyUtils.CURRENCY_MANAGER)) {
            Money managerBalance = user.calculateManagerBalance();
            float managerLevel = user.managerLevelFromPoints(managerBalance);

            evaluateAchievements(user, (long) managerLevel, new HashMap<AchievementType, Integer>() {{
                put(AchievementType.MANAGER_LEVEL_3, 3);
                put(AchievementType.MANAGER_LEVEL_4, 4);
                put(AchievementType.MANAGER_LEVEL_5, 5);
            }});
        }

        Logger.debug("WonPrize {}: User: {}: Contest: {} Position: {}",
                contest.simulation ? "Simulation" : "Official",
                user.firstName + " " + user.lastName,
                contest.name,
                contestEntry.position);
    }

    static void playedSoccerPlayers(Contest contest) {
        List<TemplateMatchEvent> matchEvents = contest.getTemplateMatchEvents();
        for (ContestEntry contestEntry : contest.contestEntries) {
            playedWithContestEntry(contestEntry, matchEvents, contest);
        }
    }

    static void playedWithContestEntry(ContestEntry contestEntry, List<TemplateMatchEvent> matchEvents, Contest contest) {
        User user = User.findOne(contestEntry.userId);

        // GOALKEEPER
        List<ObjectId> goalKeeperIdList = contest.getInstanceSoccerPlayersWithFieldPos(FieldPos.GOALKEEPER, contestEntry)
                .stream()
                .map( instanceSoccerPlayer -> instanceSoccerPlayer.templateSoccerPlayerId )
                .collect(Collectors.toList());
        List<LiveFantasyPoints> goalKeeperList = getLiveFantasyPoints(goalKeeperIdList, matchEvents);
        goalKeeperList.stream().filter(liveFantasyPoints -> liveFantasyPoints != null).forEach(liveFantasyPoints -> userPlayedWithGoalKeeper(user, liveFantasyPoints));

        // DEFENDER
        List<ObjectId> defenderIdList = contest.getInstanceSoccerPlayersWithFieldPos(FieldPos.DEFENSE, contestEntry)
                .stream()
                .map( instanceSoccerPlayer -> instanceSoccerPlayer.templateSoccerPlayerId )
                .collect(Collectors.toList());
        List<LiveFantasyPoints> defenderList = getLiveFantasyPoints(defenderIdList, matchEvents);
        defenderList.stream().filter(liveFantasyPoints -> liveFantasyPoints != null).forEach(liveFantasyPoints -> userPlayedWithDefense(user, liveFantasyPoints));

        // MIDDLE
        List<ObjectId> middleIdList = contest.getInstanceSoccerPlayersWithFieldPos(FieldPos.MIDDLE, contestEntry)
                .stream()
                .map( instanceSoccerPlayer -> instanceSoccerPlayer.templateSoccerPlayerId )
                .collect(Collectors.toList());
        List<LiveFantasyPoints> middleList = getLiveFantasyPoints(middleIdList, matchEvents);
        middleList.stream().filter( liveFantasyPoints -> liveFantasyPoints != null ).forEach(liveFantasyPoints -> userPlayedWithMiddle(user, liveFantasyPoints));

        // FORWARD
        List<ObjectId> forwardIdList = contest.getInstanceSoccerPlayersWithFieldPos(FieldPos.FORWARD, contestEntry)
                .stream()
                .map( instanceSoccerPlayer -> instanceSoccerPlayer.templateSoccerPlayerId )
                .collect(Collectors.toList());
        List<LiveFantasyPoints> forwardList = getLiveFantasyPoints(forwardIdList, matchEvents);
        forwardList.stream().filter( liveFantasyPoints -> liveFantasyPoints != null ).forEach(liveFantasyPoints -> userPlayedWithForward(user, liveFantasyPoints));

        // ALL SOCCER PLAYERS WITH POINTS
        boolean allSoccerPlayersWithPoints =
                goalKeeperList.stream().allMatch( liveFantasyPoints -> liveFantasyPoints != null && liveFantasyPoints.points > 0 ) &&
                defenderList.stream().allMatch( liveFantasyPoints -> liveFantasyPoints != null && liveFantasyPoints.points > 0 ) &&
                middleList.stream().allMatch( liveFantasyPoints -> liveFantasyPoints != null && liveFantasyPoints.points > 0 ) &&
                forwardList.stream().allMatch( liveFantasyPoints -> liveFantasyPoints != null && liveFantasyPoints.points > 0 );

        if (allSoccerPlayersWithPoints) {
            user.achievedAchievement(AchievementType.ALL_SOCCER_PLAYERS_WITH_FP);
        }
    }

    static void userPlayedWithSoccerPlayer(User user, LiveFantasyPoints liveFantasyPoints) {
        if (liveFantasyPoints.points > 0) {
            evaluateAchievements(user, liveFantasyPoints.points, new HashMap<AchievementType, Integer>() {{
                put(AchievementType.SOCCER_PLAYER_WON_FP_200, FantasyPoints(200));    // Los fantasyPoints están expresados como "coma fija" (con un decimal)
            }});
        }
    }

    static void userPlayedWithGoalKeeper(User user, LiveFantasyPoints liveFantasyPoints) {
        userPlayedWithSoccerPlayer(user, liveFantasyPoints);

        long savesShoots = liveFantasyPoints.countEvents(ImmutableList.of("SAVE_GOALKEEPER"));
        evaluateAchievements(user, savesShoots, new HashMap<AchievementType, Integer>() {{
            put(AchievementType.GOALKEEPER_SAVES_20_SHOTS, 20);
        }});

        long goalsReceived = liveFantasyPoints.countEvents(ImmutableList.of("GOAL_CONCEDED"));
        if (goalsReceived == 0) {
            user.achievedAchievement(AchievementType.GOALKEEPER_0_GOAL_RECEIVED);
        }

        long redCard = liveFantasyPoints.countEvents(ImmutableList.of("RED_CARD"));
        if (redCard > 0) {
            user.achievedAchievement(AchievementType.GOALKEEPER_RED_CARD);
        }

        long goals = liveFantasyPoints.countEvents(ImmutableList.of("GOAL_SCORED_BY_GOALKEEPER"));
        if (goals > 0) {
            user.achievedAchievement(AchievementType.GOALKEEPER_GOAL_SCORED);
        }
    }

    static void userPlayedWithDefense(User user, LiveFantasyPoints liveFantasyPoints) {
        userPlayedWithSoccerPlayer(user, liveFantasyPoints);

        long interceptions = liveFantasyPoints.countEvents(ImmutableList.of("INTERCEPTION"));
        evaluateAchievements(user, interceptions, new HashMap<AchievementType, Integer>() {{
            put(AchievementType.DEFENDER_30_INTERCEPTIONS, 30);
        }});
    }

    static void userPlayedWithMiddle(User user, LiveFantasyPoints liveFantasyPoints) {
        userPlayedWithSoccerPlayer(user, liveFantasyPoints);

        long passes = liveFantasyPoints.countEvents(ImmutableList.of("PASS_SUCCESSFUL"));
        evaluateAchievements(user, passes, new HashMap<AchievementType, Integer>() {{
            put(AchievementType.MIDDLE_70_PASS_SUCCESSFUL, 70);
        }});
    }

    static void userPlayedWithForward(User user, LiveFantasyPoints liveFantasyPoints) {
        userPlayedWithSoccerPlayer(user, liveFantasyPoints);

        long goals = liveFantasyPoints.countEvents(ImmutableList.of("GOAL_SCORED_BY_FORWARD"));
        evaluateAchievements(user, goals, new HashMap<AchievementType, Integer>() {{
            put(AchievementType.FORWARD_4_GOALS, 4);
        }});
    }

    static List<LiveFantasyPoints> getLiveFantasyPoints(List<ObjectId> soccerPlayerList, List<TemplateMatchEvent> matchEvents) {
        return soccerPlayerList.stream().map(soccerPlayerId -> getLiveFantasyPoints(soccerPlayerId, matchEvents) ).collect(Collectors.toList());
    }

    static LiveFantasyPoints getLiveFantasyPoints(ObjectId soccerPlayerId, List<TemplateMatchEvent> matchEvents) {
        LiveFantasyPoints result = null;
        for (TemplateMatchEvent matchEvent : matchEvents) {
            result = matchEvent.getLiveFantasyPointsBySoccerPlayer(soccerPlayerId);
            if (result != null) {
                break;
            }
        }
        return result;
    }

    static void evaluateAchievements(User user, long points, HashMap<AchievementType, Integer> achievementsMap) {
        achievementsMap.forEach((key, value) -> {
            if (points >= value) {
                user.achievedAchievement(key);
            }
        });
    }
}
