package model;

import org.joda.money.Money;
import play.Logger;
import utils.MoneyUtils;
import org.bson.types.ObjectId;

import java.util.List;
import java.util.stream.Collectors;
import com.google.common.collect.ImmutableList;

public class Achievement {
    static public void trueSkillChanged(User user, Contest contest) {

        //
        // TRUE_SKILL_N
        //
        if (user.trueSkill >= 500) {
            user.achievedAchievement(AchievementType.TRUE_SKILL_500);
        }

        if (user.trueSkill >= 600) {
            user.achievedAchievement(AchievementType.TRUE_SKILL_600);
        }

        if (user.trueSkill >= 700) {
            user.achievedAchievement(AchievementType.TRUE_SKILL_700);
        }

        if (user.trueSkill >= 800) {
            user.achievedAchievement(AchievementType.TRUE_SKILL_800);
        }

        if (user.trueSkill >= 900) {
            user.achievedAchievement(AchievementType.TRUE_SKILL_900);
        }

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
        long played = Contest.countPlayedSimulations(user.userId);

        //
        // WON_N_VIRTUAL_CONTEST
        //
        user.achievedAchievement(AchievementType.WON_1_VIRTUAL_CONTEST);

        if (played >= 5) {
            user.achievedAchievement(AchievementType.WON_5_VIRTUAL_CONTESTS);
        }

        if (played >= 10) {
            user.achievedAchievement(AchievementType.WON_10_VIRTUAL_CONTESTS);
        }

        //
        // FP_N_VIRTUAL_CONTEST
        //
        if (contestEntry.fantasyPoints >= 500) {
            user.achievedAchievement(AchievementType.FP_500_VIRTUAL_CONTEST);
        }

        if (contestEntry.fantasyPoints >= 700) {
            user.achievedAchievement(AchievementType.FP_700_VIRTUAL_CONTEST);
        }

        if (contestEntry.fantasyPoints >= 1000) {
            user.achievedAchievement(AchievementType.FP_1000_VIRTUAL_CONTEST);
        }

        //
        // DIFF_FP_N_VIRTUAL_CONTEST
        //
        ContestEntry second = contest.getContestEntryInPosition(1);
        long diffFP = contestEntry.fantasyPoints - second.fantasyPoints;

        if (diffFP >= 100) {
            user.achievedAchievement(AchievementType.DIFF_FP_100_VIRTUAL_CONTEST);
        }

        if (diffFP >= 200) {
            user.achievedAchievement(AchievementType.DIFF_FP_200_VIRTUAL_CONTEST);
        }

        if (diffFP >= 300) {
            user.achievedAchievement(AchievementType.DIFF_FP_300_VIRTUAL_CONTEST);
        }

        return played;
    }

    static private long playedSimulationContest(User user, ContestEntry contestEntry, Contest contest) {
        long played = Contest.countPlayedSimulations(user.userId);

        //
        // PLAYED_N_VIRTUAL_CONTESTS
        //
        if (played >= 5) {
            user.achievedAchievement(AchievementType.PLAYED_5_VIRTUAL_CONTESTS);
        }

        if (played >= 10) {
            user.achievedAchievement(AchievementType.PLAYED_10_VIRTUAL_CONTESTS);
        }

        return played;
    }

    static private long wonOfficialContest(User user, ContestEntry contestEntry, Contest contest) {
        long played = Contest.countPlayedOfficial(user.userId);

        //
        // WON_N_OFFICIAL_CONTEST
        //
        user.achievedAchievement(AchievementType.WON_1_OFFICIAL_CONTEST);

        if (played >= 5) {
            user.achievedAchievement(AchievementType.WON_5_OFFICIAL_CONTESTS);
        }

        if (played >= 10) {
            user.achievedAchievement(AchievementType.WON_10_OFFICIAL_CONTESTS);
        }

        //
        // FP_N_OFFICIAL_CONTEST
        //
        if (contestEntry.fantasyPoints >= 500) {
            user.achievedAchievement(AchievementType.FP_500_OFFICIAL_CONTEST);
        }

        if (contestEntry.fantasyPoints >= 700) {
            user.achievedAchievement(AchievementType.FP_700_OFFICIAL_CONTEST);
        }

        if (contestEntry.fantasyPoints >= 1000) {
            user.achievedAchievement(AchievementType.FP_1000_OFFICIAL_CONTEST);
        }

        //
        // DIFF_FP_N_OFFICIAL_CONTEST
        //
        ContestEntry second = contest.getContestEntryInPosition(1);
        long diffFP = contestEntry.fantasyPoints - second.fantasyPoints;

        if (diffFP >= 100) {
            user.achievedAchievement(AchievementType.DIFF_FP_100_OFFICIAL_CONTEST);
        }

        if (diffFP >= 200) {
            user.achievedAchievement(AchievementType.DIFF_FP_200_OFFICIAL_CONTEST);
        }

        if (diffFP >= 300) {
            user.achievedAchievement(AchievementType.DIFF_FP_300_OFFICIAL_CONTEST);
        }

        return played;
    }

    static private long playedOfficialContest(User user, ContestEntry contestEntry, Contest contest) {
        long played = Contest.countPlayedOfficial(user.userId);

        //
        // PLAYED_N_OFFICIAL_CONTESTS
        //
        if (played >= 5) {
            user.achievedAchievement(AchievementType.PLAYED_5_OFFICIAL_CONTESTS);
        }

        if (played >= 10) {
            user.achievedAchievement(AchievementType.PLAYED_10_OFFICIAL_CONTESTS);
        }

        return played;
    }

    static private void wonPrize(User user, ContestEntry contestEntry, Contest contest) {
        // Ganó manager points?
        if (contestEntry.prize.getCurrencyUnit().equals(MoneyUtils.CURRENCY_MANAGER)) {
            Money managerBalance = user.calculateManagerBalance();
            float managerLevel = user.managerLevelFromPoints(managerBalance);

            if (managerLevel >= 3) {
                user.achievedAchievement(AchievementType.MANAGER_LEVEL_3);
            }

            if (managerLevel >= 4) {
                user.achievedAchievement(AchievementType.MANAGER_LEVEL_4);
            }

            if (managerLevel >= 5) {
                user.achievedAchievement(AchievementType.MANAGER_LEVEL_5);
            }
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

            if (liveFantasyPoints.points > 100) {
                user.achievedAchievement(AchievementType.SOCCER_PLAYER_WON_FP_100);
            }

            if (liveFantasyPoints.points > 200) {
                user.achievedAchievement(AchievementType.SOCCER_PLAYER_WON_FP_200);
            }
        }
    }

    static void userPlayedWithGoalKeeper(User user, LiveFantasyPoints liveFantasyPoints) {
        userPlayedWithSoccerPlayer(user, liveFantasyPoints);

        long savesShoots = liveFantasyPoints.countEvents(ImmutableList.of("SAVE_GOALKEEPER"));

        if (savesShoots >= 10) {
            user.achievedAchievement(AchievementType.GOALKEEPER_SAVES_10_SHOTS);
        }

        if (savesShoots >= 20) {
            user.achievedAchievement(AchievementType.GOALKEEPER_SAVES_20_SHOTS);
        }

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

        if (interceptions >= 10) {
            user.achievedAchievement(AchievementType.DEFENDER_10_INTERCEPTIONS);
        }

        if (interceptions >= 20) {
            user.achievedAchievement(AchievementType.DEFENDER_20_INTERCEPTIONS);
        }
    }

    static void userPlayedWithMiddle(User user, LiveFantasyPoints liveFantasyPoints) {
        userPlayedWithSoccerPlayer(user, liveFantasyPoints);

        long passes = liveFantasyPoints.countEvents(ImmutableList.of("PASS_SUCCESSFUL"));

        if (passes >= 10) {
            user.achievedAchievement(AchievementType.MIDDLE_10_PASS_SUCCESSFUL);
        }

        if (passes >= 20) {
            user.achievedAchievement(AchievementType.MIDDLE_20_PASS_SUCCESSFUL);
        }
    }

    static void userPlayedWithForward(User user, LiveFantasyPoints liveFantasyPoints) {
        userPlayedWithSoccerPlayer(user, liveFantasyPoints);

        long goals = liveFantasyPoints.countEvents(ImmutableList.of("GOAL_SCORED_BY_FORWARD"));

        if (goals >= 1) {
            user.achievedAchievement(AchievementType.FORWARD_1_GOAL);
        }

        if (goals >= 2) {
            user.achievedAchievement(AchievementType.FORWARD_2_GOALS);
        }

        if (goals >= 3) {
            user.achievedAchievement(AchievementType.FORWARD_3_GOALS);
        }
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
}
