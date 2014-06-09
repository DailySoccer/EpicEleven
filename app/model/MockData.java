package model;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import play.Logger;
import play.Play;
import java.util.Date;

import java.util.ArrayList;


public final class MockData {

    static public void ensureMockDataAll() {
        if (Play.isProd())
            return;

        ensureMockDataContests();
    }

    static public void ensureMockDataContests() {
        if (Play.isProd())
            return;

        createTemplateSoccerTeamsAndPlayers();
        createTemplateContests();
        instantiateContests();
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

                TemplateContest templateContest = new TemplateContest();

                templateContest.name = String.format("Contest%02d-%02d date %s", dayCounter, contestCounter, currentCreationDay);
                templateContest.postName = "Late evening";
                templateContest.minInstances = 3;
                templateContest.maxEntries = 10;
                templateContest.prizeType = PrizeType.STANDARD;
                templateContest.entryFee = (contestCounter + 1) * 10;
                templateContest.salaryCap = 100000;
                templateContest.startDate = currentCreationDay.toDate();
                templateContest.templateMatchEventIds = new ArrayList<>();

                for (int teamCounter = 0; teamCounter < 20; teamCounter += 2) {
                    TemplateMatchEvent newMatch = createTemplateMatchEvent(String.format("Team%02d", teamCounter),
                            String.format("Team%02d", teamCounter + 1),
                            currentCreationDay);

                    templateContest.templateMatchEventIds.add(newMatch.templateMatchEventId);
                }

                Model.templateContests().insert(templateContest);
            }

            currentCreationDay = currentCreationDay.plusDays(7);
        }
    }

    static private TemplateMatchEvent createTemplateMatchEvent(final String nameTeamA, final String nameTeamB, final DateTime dateTime) {
        // Logger.info("MockData: MatchEvent: {} vs {} ({})", nameTeamA, nameTeamB, dateTime.toString("yyyy.MM.dd G 'at' HH:mm:ss z"));

        TemplateMatchEvent templateMatchEvent = new TemplateMatchEvent();
        templateMatchEvent.startDate = dateTime.toDate();

        TemplateSoccerTeam teamA = Model.templateSoccerTeams().findOne("{ name: '#' }", nameTeamA).as(TemplateSoccerTeam.class);
        TemplateSoccerTeam teamB = Model.templateSoccerTeams().findOne("{ name: '#' }", nameTeamB).as(TemplateSoccerTeam.class);

        // setup Team A (incrustando a los futbolistas en el equipo)
        SoccerTeam newTeamA = new SoccerTeam();
        newTeamA.templateSoccerTeamId = teamA.templateSoccerTeamId;
        newTeamA.name = nameTeamA;
        Iterable<TemplateSoccerPlayer> playersTeamA = Model.templateSoccerPlayers().find("{ templateTeamId: # }", teamA.templateSoccerTeamId).as(TemplateSoccerPlayer.class);
        for(TemplateSoccerPlayer templateSoccer : playersTeamA) {
            newTeamA.soccerPlayers.add(new SoccerPlayer(templateSoccer));
        }
        templateMatchEvent.soccerTeamA = newTeamA;

        // setup Team B (incrustando a los futbolistas en el equipo)
        SoccerTeam newTeamB = new SoccerTeam();
        newTeamB.templateSoccerTeamId = teamB.templateSoccerTeamId;
        newTeamB.name = nameTeamB;
        Iterable<TemplateSoccerPlayer> playersTeamB = Model.templateSoccerPlayers().find("{ templateTeamId: # }", teamB.templateSoccerTeamId).as(TemplateSoccerPlayer.class);
        for(TemplateSoccerPlayer templateSoccer : playersTeamB) {
            newTeamB.soccerPlayers.add(new SoccerPlayer(templateSoccer));
        }
        templateMatchEvent.soccerTeamB = newTeamB;

        Model.templateMatchEvents().insert(templateMatchEvent);

        return templateMatchEvent;
    }

    static private void instantiateContests() {
        // Creamos los contest de la primera jornada
        Date startDate = new DateTime(2014, 10, 14, 12, 0, DateTimeZone.UTC).toDate();

        Iterable<TemplateContest> templateContests = Model.templateContests().find("{startDate: #}", startDate).as(TemplateContest.class);
        for(TemplateContest template : templateContests) {
            for(int i=0; i<template.minInstances; i++) {
                Contest contest = new Contest(template);
                contest.maxUsers = 10;
                Model.contests().insert(contest);
            }
        }
    }
}