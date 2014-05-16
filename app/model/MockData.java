package model;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import play.Play;

import java.util.ArrayList;


public final class MockData {

    static public void ensureDBMockData() {
        if (Play.isProd())
            return;

        createPrefabSoccerTeamsAndPlayers();
        createPrefabContests();
    }

    static private void createPrefabSoccerTeamsAndPlayers() {
        FieldPos[] pos = new FieldPos[] { FieldPos.GOALKEEPER,
                                          FieldPos.DEFENSE, FieldPos.DEFENSE, FieldPos.DEFENSE, FieldPos.DEFENSE,
                                          FieldPos.MIDDLE, FieldPos.MIDDLE, FieldPos.MIDDLE, FieldPos.MIDDLE,
                                          FieldPos.FORWARD, FieldPos.FORWARD };

        for (int teamCounter = 0; teamCounter < 20; ++teamCounter) {

            PrefabSoccerTeam soccerTeam = new PrefabSoccerTeam();
            soccerTeam.name = String.format("Team%02d", teamCounter);

            Model.prefabSoccerTeams().insert(soccerTeam);

            for (int playerCounter = 0; playerCounter < 11; ++playerCounter) {
                PrefabSoccerPlayer soccerPlayer = new PrefabSoccerPlayer();
                soccerPlayer.name = String.format("Player%02d-%02d", teamCounter, playerCounter);
                soccerPlayer.fieldPos = pos[playerCounter];
                soccerPlayer.salary = (playerCounter + 1) * 10000;
                soccerPlayer.prefabTeamId = soccerTeam.prefabSoccerTeamId;

                Model.prefabSoccerPlayers().insert(soccerPlayer);
            }
        }
    }

    static private void createPrefabContests() {

        DateTime currentCreationDay =  new DateTime(2014, 10, 14, 12, 0, DateTimeZone.UTC);

        for (int dayCounter = 0; dayCounter < 20; ++dayCounter) {                 // Durante 20 jornadas
            for (int contestCounter = 0; contestCounter < 3; ++contestCounter) {  // 3 concursos por jornada

                PrefabContest contest = new PrefabContest();

                contest.name = String.format("Contest%02d-%02d date %s", dayCounter, contestCounter, currentCreationDay);
                contest.postName = "Late evening";
                contest.minInstances = 3;
                contest.maxEntries = 10;
                contest.prizeType = PrizeType.STANDARD;
                contest.entryFee = (contestCounter + 1) * 10;
                contest.salaryCap = 100000;
                contest.prefabMatchEventIds = new ArrayList<>();

                for (int teamCounter = 0; teamCounter < 20; teamCounter += 2) {
                    PrefabMatchEvent newMatch = createPrefabMatchEvent(String.format("Team%02d", teamCounter),
                                                                       String.format("Team%02d", teamCounter + 1),
                                                                       currentCreationDay);

                    contest.prefabMatchEventIds.add(newMatch.prefabMatchEventId);
                }

                Model.prefabContests().insert(contest);
            }

            currentCreationDay.plusDays(7);
        }
    }

    static private PrefabMatchEvent createPrefabMatchEvent(final String nameTeamA, final String nameTeamB, final DateTime dateTime) {
        PrefabMatchEvent prefabMatchEvent = new PrefabMatchEvent();
        prefabMatchEvent.startDate = dateTime.toDate();

        PrefabSoccerTeam teamA = Model.prefabSoccerTeams().findOne("{ name: '#' }", nameTeamA).as(PrefabSoccerTeam.class);
        PrefabSoccerTeam teamB = Model.prefabSoccerTeams().findOne("{ name: '#' }", nameTeamB).as(PrefabSoccerTeam.class);

        prefabMatchEvent.prefabSoccerTeamAId = teamA.prefabSoccerTeamId;
        prefabMatchEvent.prefabSoccerTeamBId = teamB.prefabSoccerTeamId;

        Model.prefabMatchEvents().insert(prefabMatchEvent);

        return prefabMatchEvent;
    }

    static private void createContests() {

        DateTime currentCreationDay =  new DateTime(2014, 10, 14, 12, 0, DateTimeZone.UTC);

        for (int dayCounter = 0; dayCounter < 10; ++dayCounter) {
            for (int contestCounter = 0; contestCounter < 10; ++contestCounter) {

                Contest contest = new Contest();

                contest.matchEventIds = new ArrayList<>();
                contest.currentUserIds = new ArrayList<>();
                contest.maxUsers = 10;
                contest.prizeType = PrizeType.STANDARD;
                contest.entryFee = 10;
                contest.salaryCap = 1000000;

                contest.name = "Contest " + contestCounter + " date " + currentCreationDay;

                for (int teamCounter = 0; teamCounter < 12; teamCounter += 2) {
                    MatchEvent newMatch = createMatchEvent(String.format("Team%02d", teamCounter),
                                                           String.format("Team%02d", teamCounter + 1),
                                                           currentCreationDay);
                    Model.matchEvents().insert(newMatch);
                    contest.matchEventIds.add(newMatch.matchEventId);
                }

                Model.contests().insert(contest);
            }

            currentCreationDay.plusDays(3);
        }
    }

    static private MatchEvent createMatchEvent(final String teamA, final String teamB, final DateTime dateTime) {

        MatchEvent matchEvent = new MatchEvent();
        matchEvent.startDate = dateTime.toDate();
        matchEvent.soccerTeamA = createTeam(teamA);
        matchEvent.soccerTeamB = createTeam(teamB);

        return matchEvent;
    }

    static private SoccerTeam createTeam(final String teamName) {
        return new SoccerTeam() {{
            name = teamName;
            soccerPlayers = new ArrayList<SoccerPlayer>() {{
                add(createSoccerPlayer(teamName, 0, FieldPos.GOALKEEPER));

                add(createSoccerPlayer(teamName, 1, FieldPos.DEFENSE));
                add(createSoccerPlayer(teamName, 2, FieldPos.DEFENSE));

                add(createSoccerPlayer(teamName, 3, FieldPos.MIDDLE));
                add(createSoccerPlayer(teamName, 4, FieldPos.MIDDLE));

                add(createSoccerPlayer(teamName, 5, FieldPos.FORWARD));
                add(createSoccerPlayer(teamName, 6, FieldPos.FORWARD));
            }};
        }};
    }

    static private SoccerPlayer createSoccerPlayer(final String teamName, final int index, final FieldPos pos) {
       return new SoccerPlayer() {{
           name = String.format("SoccerPlayer%02d ", index) + pos.toString() + " " + teamName;
           fieldPos = pos;
           salary = 1000000;
       }};
    }
}