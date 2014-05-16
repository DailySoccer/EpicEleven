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

    static private void instantiateContests() {

    }

    static private MatchEvent instantiateMatchEvent() {

        return null;
    }

    static private SoccerTeam instantiateSoccerTeam() {


        return null;
    }
}