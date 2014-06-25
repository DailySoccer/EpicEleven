package model;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import play.Logger;
import play.Play;
import java.util.Date;

import java.util.ArrayList;


public final class MockData {

    static public void ensureMockDataAll() {
        ensureMockDataContests();
    }

    static public void ensureMockDataContests() {
        createTemplateSoccerTeamsAndPlayers();
        createTemplateContests();
        instantiateContests();
    }

    public static void createPointsTranslation() {
        int[][] pointsTable = {
                {1, 2},       // pase
                {3, 10},      // regate
                {4, 15},      // falta recibida
                {7, 15},      // recuperacion/entrada
                {8, 15},      // intercepcion
                {10, 20},     // parada
                {11, 20},     // captura balon
                {12, 10},     // parada
                {13, 20},     // tiro a puerta
                {14, 20},     // tiro a puerta
                {15, 20},     // tiro a puerta
                {210, 20},    // asistencia
                //{16, 100},    // gol
                {1601, 100},    // gol del portero
                {1602, 80},    // gol del defensa
                {1603, 60},    // gol del medio
                {1604, 40},    // gol del delantero
                {1699, -10},  // gol en contra
                {17, -50},    // tarjeta amarilla
                {41, 10},     // despeje puños
                {50, -20},    // perdida de balon
                {51, -20},    // perdida de balon
                {72, -5},     // fuera de juego
                {1004, -5},   // falta infligida
                {1017, -200}, // tarjeta roja
                {1409, -30},  // penalty infligido
                {1410, -30},  // penalty fallado
                {1058, 30},   // penalty parado por el portero
                {2000, 40},   // clean sheet
                {2001, -10},  // Gol al defensa
        };
        //TODO: Gol en contra:
        for (int i = 0; i < pointsTable.length; i++){
            PointsTranslation myPointsTranslation = new PointsTranslation();
            myPointsTranslation.eventTypeId = pointsTable[i][0];
            PointsTranslation pointsTranslation = Model.pointsTranslation().findOne("{eventTypeId: #}", myPointsTranslation.eventTypeId).as(PointsTranslation.class);
            if (pointsTranslation == null){
                myPointsTranslation.unixtimestamp = 0L;
                myPointsTranslation.timestamp = new Date(myPointsTranslation.unixtimestamp);
                myPointsTranslation.points = pointsTable[i][1];
                Model.pointsTranslation().insert(myPointsTranslation);
            }
        }
    }

    static private void createTemplateSoccerTeamsAndPlayers() {
        FieldPos[] pos = new FieldPos[] { FieldPos.GOALKEEPER,
                                          FieldPos.DEFENSE, FieldPos.DEFENSE, FieldPos.DEFENSE, FieldPos.DEFENSE,
                                          FieldPos.MIDDLE, FieldPos.MIDDLE, FieldPos.MIDDLE, FieldPos.MIDDLE,
                                          FieldPos.FORWARD, FieldPos.FORWARD };

        for (int teamCounter = 0; teamCounter < 20; ++teamCounter) {

            TemplateSoccerTeam soccerTeam = new TemplateSoccerTeam();
            soccerTeam.optaTeamId = String.valueOf(++optaTeamId);
            soccerTeam.name = String.format("Team%02d", teamCounter);

            Model.templateSoccerTeams().insert(soccerTeam);

            for (int playerCounter = 0; playerCounter < 11; ++playerCounter) {
                TemplateSoccerPlayer soccerPlayer = new TemplateSoccerPlayer();
                soccerPlayer.optaPlayerId = String.valueOf(++optaPlayerId);
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

    static public void createTemplateContest(final DateTime dateTime) {
        TemplateContest templateContest = new TemplateContest();

        templateContest.name = String.format("Contest date %s", dateTime);
        templateContest.postName = "Late evening";
        templateContest.minInstances = 3;
        templateContest.maxEntries = 10;
        templateContest.prizeType = PrizeType.STANDARD;
        templateContest.entryFee = 10000;
        templateContest.salaryCap = 100000;
        templateContest.startDate = dateTime.toDate();
        templateContest.templateMatchEventIds = new ArrayList<>();

        // Buscar todos los template match events que jueguen ese dia
        Iterable<TemplateMatchEvent> lMatchEvents = Model.templateMatchEvents().find("{startDate: #}", dateTime.toDate()).as(TemplateMatchEvent.class);
        for (TemplateMatchEvent match: lMatchEvents) {
            templateContest.templateMatchEventIds.add(match.templateMatchEventId);
        }

        Logger.info("MockData: Template Contest: {} ({})", templateContest.templateMatchEventIds, dateTime.toString("yyyy.MM.dd G 'at' HH:mm:ss z"));

        Model.templateContests().insert(templateContest);
    }

    static public TemplateMatchEvent createTemplateMatchEvent(final DateTime dateTime) {
        TemplateMatchEvent templateMatchEvent = new TemplateMatchEvent();
        templateMatchEvent.startDate = dateTime.toDate();

        Iterable<TemplateSoccerTeam> randomTeamA = Model.getRandomDocument(Model.templateSoccerTeams()).as(TemplateSoccerTeam.class);
        TemplateSoccerTeam teamA = (randomTeamA.iterator().hasNext()) ? randomTeamA.iterator().next() : null;

        Iterable<TemplateSoccerTeam> randomTeamB = Model.getRandomDocument(Model.templateSoccerTeams()).as(TemplateSoccerTeam.class);
        TemplateSoccerTeam teamB = (randomTeamB.iterator().hasNext()) ? randomTeamB.iterator().next() : null;

        return (teamA != null && teamB != null)
                ? createTemplateMatchEvent(teamA.name, teamB.name, dateTime)
                : null;
    }

    static private TemplateMatchEvent createTemplateMatchEvent(final String nameTeamA, final String nameTeamB, final DateTime dateTime) {
        TemplateSoccerTeam teamA = Model.templateSoccerTeams().findOne("{ name: '#' }", nameTeamA).as(TemplateSoccerTeam.class);
        TemplateSoccerTeam teamB = Model.templateSoccerTeams().findOne("{ name: '#' }", nameTeamB).as(TemplateSoccerTeam.class);

        return Model.createTemplateMatchEvent(teamA, teamB, dateTime.toDate());
    }

    static private void instantiateContests() {
        // Creamos los contest de la primera jornada
        instantiateContests(new DateTime(2014, 10, 14, 12, 0, DateTimeZone.UTC));
    }

    static public void instantiateContests(final DateTime dateTime) {
        Date startDate = dateTime.toDate();

        Iterable<TemplateContest> templateContests = Model.templateContests().find("{startDate: #}", startDate).as(TemplateContest.class);
        for(TemplateContest template : templateContests) {
            for(int i=0; i<template.minInstances; i++) {
                Contest contest = new Contest(template);
                contest.maxUsers = 10;
                Model.contests().insert(contest);
            }
        }
    }

    static int optaPlayerId = 0;
    static int optaTeamId = 0;
}