package model;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import play.Play;

import java.util.ArrayList;


public final class MockData {

    static public void ensureDBMockData() {
        if (Play.isProd())
            return;

        createTemplateSoccerTeamsAndPlayers();
        createTemplateContests();
    }

    static private void createTemplateSoccerTeamsAndPlayers() {
        FieldPos[] pos = new FieldPos[] { FieldPos.GOALKEEPER,
                                          FieldPos.DEFENSE, FieldPos.DEFENSE, FieldPos.DEFENSE, FieldPos.DEFENSE,
                                          FieldPos.MIDDLE, FieldPos.MIDDLE, FieldPos.MIDDLE, FieldPos.MIDDLE,
                                          FieldPos.FORWARD, FieldPos.FORWARD };

        for (int teamCounter = 0; teamCounter < 20; ++teamCounter) {

            TemplateSoccerTeam soccerTeam = new TemplateSoccerTeam();
            soccerTeam.name = String.format("Team%02d", teamCounter);

            Model.templateSoccerTeams().insert(soccerTeam);

            for (int playerCounter = 0; playerCounter < 11; ++playerCounter) {
                TemplateSoccerPlayer soccerPlayer = new TemplateSoccerPlayer();
                soccerPlayer.name = String.format("Player%02d-%02d", teamCounter, playerCounter);
                soccerPlayer.fieldPos = pos[playerCounter];
                soccerPlayer.salary = (playerCounter + 1) * 10000;
                soccerPlayer.templateTeamId = soccerTeam.templateSoccerTeamId;

                Model.templateSoccerPlayers().insert(soccerPlayer);
            }
        }
    }

    static private void createTemplateContests() {

        DateTime currentCreationDay =  new DateTime(2014, 10, 14, 12, 0, DateTimeZone.UTC);

        for (int dayCounter = 0; dayCounter < 20; ++dayCounter) {                 // Durante 20 jornadas
            for (int contestCounter = 0; contestCounter < 3; ++contestCounter) {  // 3 concursos por jornada

                TemplateContest contest = new TemplateContest();

                contest.name = String.format("Contest%02d-%02d date %s", dayCounter, contestCounter, currentCreationDay);
                contest.postName = "Late evening";
                contest.minInstances = 3;
                contest.maxEntries = 10;
                contest.prizeType = PrizeType.STANDARD;
                contest.entryFee = (contestCounter + 1) * 10;
                contest.salaryCap = 100000;
                contest.templatebMatchEventIds = new ArrayList<>();

                for (int teamCounter = 0; teamCounter < 20; teamCounter += 2) {
                    TemplateMatchEvent newMatch = createTemplateMatchEvent(String.format("Team%02d", teamCounter),
                            String.format("Team%02d", teamCounter + 1),
                            currentCreationDay);

                    contest.templatebMatchEventIds.add(newMatch.templateMatchEventId);
                }

                Model.templateContests().insert(contest);
            }

            currentCreationDay.plusDays(7);
        }
    }

    static private TemplateMatchEvent createTemplateMatchEvent(final String nameTeamA, final String nameTeamB, final DateTime dateTime) {
        TemplateMatchEvent templateMatchEvent = new TemplateMatchEvent();
        templateMatchEvent.startDate = dateTime.toDate();

        TemplateSoccerTeam teamA = Model.templateSoccerTeams().findOne("{ name: '#' }", nameTeamA).as(TemplateSoccerTeam.class);
        TemplateSoccerTeam teamB = Model.templateSoccerTeams().findOne("{ name: '#' }", nameTeamB).as(TemplateSoccerTeam.class);

        templateMatchEvent.templateSoccerTeamAId = teamA.templateSoccerTeamId;
        templateMatchEvent.templateSoccerTeamBId = teamB.templateSoccerTeamId;

        Model.templateMatchEvents().insert(templateMatchEvent);

        return templateMatchEvent;
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