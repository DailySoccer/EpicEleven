package model;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;

import java.util.ArrayList;


public class MockData {

    static public void ensureDBMockData() {
        if (Model.contests().count() == 0) {
            createContests();
        }
    }

    static private void createContests() {

        DateTime currentCreationDay =  new DateTime(2014, 10, 14, 12, 0, DateTimeZone.UTC);

        for (int dayCounter = 0; dayCounter < 10; ++dayCounter) {
            for (int contestCounter = 0; contestCounter < 10; ++contestCounter) {
                Contest contest = new Contest();
                contest.matchEvents = new ArrayList<>();
                for (int teamCounter = 0; teamCounter < 12; teamCounter += 2) {
                    contest.matchEvents.add(createMatchEvent(String.format("Team%02d", teamCounter),
                            String.format("Team%02d", teamCounter + 1), currentCreationDay));
                }
                Model.contests().insert(contest);
            }

            currentCreationDay.plusDays(3);
        }
    }

    static private MatchEvent createMatchEvent(final String teamA, final String teamB, final DateTime dateTime) {

        MatchEvent matchEvent = new MatchEvent();
        matchEvent.startDate = dateTime.toDate();
        matchEvent.teamA = createTeam(teamA);
        matchEvent.teamB = createTeam(teamB);

        return matchEvent;
    }

    static private Team createTeam(final String teamName) {
        return new Team() {{
            name = teamName;
            players = new ArrayList<SoccerPlayer>() {{
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