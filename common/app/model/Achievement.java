package model;

import play.Logger;

public class Achievement {
    static public void PlayedContest(Contest contest) {
        for (ContestEntry contestEntry : contest.contestEntries) {
            PlayedContest(contestEntry, contest);
        }
    }

    static public void PlayedContest(ContestEntry contestEntry, Contest contest) {
        User user = User.findOne(contestEntry.userId);

        // Ha ganado?
        if (contestEntry.position == 0) {
            // Achievements por Ganar
            if (contest.simulation) {
                WonSimulationContest(user);
            }
            else {
                WonOfficialContest(user);
            }
        }
        else {
            // Achievements por Participar
            if (contest.simulation) {
                PlayedSimulationContest(user);
            }
            else {
                PlayedOfficialContest(user);
            }
        }

        Logger.debug("PlayerContest {}: User: {}: Contest: {} Position: {}",
                contest.simulation ? "Simulation" : "Official",
                user.firstName + " " + user.lastName,
                contest.name,
                contestEntry.position);
    }

    static public long WonSimulationContest(User user) {
        long played = Contest.countPlayedSimulations(user.userId);

        user.achievedAchievement(AchievementType.WON_1_VIRTUAL_CONTEST);

        if (played >= 5) {
            user.achievedAchievement(AchievementType.WON_5_VIRTUAL_CONTESTS);
        }

        if (played >= 10) {
            user.achievedAchievement(AchievementType.WON_10_VIRTUAL_CONTESTS);
        }

        return played;
    }

    static public long PlayedSimulationContest(User user) {
        long played = Contest.countPlayedSimulations(user.userId);


        if (played >= 5) {
            user.achievedAchievement(AchievementType.PLAYED_5_VIRTUAL_CONTESTS);
        }

        if (played >= 10) {
            user.achievedAchievement(AchievementType.PLAYED_10_VIRTUAL_CONTESTS);
        }

        return played;
    }

    static public long WonOfficialContest(User user) {
        long played = Contest.countPlayedOfficial(user.userId);

        user.achievedAchievement(AchievementType.WON_1_OFFICIAL_CONTEST);

        if (played >= 5) {
            user.achievedAchievement(AchievementType.WON_5_OFFICIAL_CONTESTS);
        }

        if (played >= 10) {
            user.achievedAchievement(AchievementType.WON_10_OFFICIAL_CONTESTS);
        }

        return played;
    }

    static public long PlayedOfficialContest(User user) {
        long played = Contest.countPlayedOfficial(user.userId);

        if (played >= 5) {
            user.achievedAchievement(AchievementType.PLAYED_5_OFFICIAL_CONTESTS);
        }

        if (played >= 10) {
            user.achievedAchievement(AchievementType.PLAYED_10_OFFICIAL_CONTESTS);
        }

        return played;
    }
}
