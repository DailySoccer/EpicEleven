package model;

import org.joda.money.Money;
import play.Logger;
import utils.MoneyUtils;

public class Achievement {
    static public void PlayedContest(Contest contest) {
        for (ContestEntry contestEntry : contest.contestEntries) {
            PlayedContest(contestEntry, contest);
        }
    }

    static private void PlayedContest(ContestEntry contestEntry, Contest contest) {
        User user = User.findOne(contestEntry.userId);

        // Ha ganado?
        if (contestEntry.position == 0) {
            // Achievements por Ganar
            if (contest.simulation) {
                WonSimulationContest(user, contestEntry, contest);
            }
            else {
                WonOfficialContest(user, contestEntry, contest);
            }
        }
        else {
            // Achievements por Participar
            if (contest.simulation) {
                PlayedSimulationContest(user, contestEntry, contest);
            }
            else {
                PlayedOfficialContest(user, contestEntry, contest);
            }
        }

        // Recibió un premio?
        if (contestEntry.prize.isPositive()) {
            wonPrize(user, contestEntry, contest);
        }

        Logger.debug("PlayedContest {}: User: {}: Contest: {} Position: {}",
                contest.simulation ? "Simulation" : "Official",
                user.firstName + " " + user.lastName,
                contest.name,
                contestEntry.position);
    }

    static private long WonSimulationContest(User user, ContestEntry contestEntry, Contest contest) {
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

    static private long PlayedSimulationContest(User user, ContestEntry contestEntry, Contest contest) {
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

    static private long WonOfficialContest(User user, ContestEntry contestEntry, Contest contest) {
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

    static private long PlayedOfficialContest(User user, ContestEntry contestEntry, Contest contest) {
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
}
