package model;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.jongo.Find;
import org.jongo.MongoCollection;
import play.Logger;

import java.util.Date;

import java.util.ArrayList;
import model.opta.OptaProcessor.OptaEventType;


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
                {OptaEventType.PASS.code, 2},       // pase
                {OptaEventType.TAKE_ON.code, 10},      // regate
                {OptaEventType.FOUL_RECEIVED.code, 10},      // falta recibida
                {OptaEventType.TACKLE_EFFECTIVE.code, 15},   // recuperacion/entrada con posesion
                {OptaEventType.INTERCEPTION.code, 15},      // intercepcion
                {OptaEventType.SAVE.code, 10},     // parada
                {OptaEventType.CLAIM.code, 10},     // captura balon
                {OptaEventType.CLEARANCE.code, 10},     // parada
                {OptaEventType.MISS.code, 15},     // tiro a puerta
                {OptaEventType.POST.code, 15},     // tiro a puerta
                {OptaEventType.ATTEMPT_SAVED.code, 15},     // tiro a puerta
                {OptaEventType.ASSIST.code, 20},    // asistencia
                //{16, 100},    // gol
                {OptaEventType.GOAL_SCORED_BY_GOALKEEPER.code, 100},    // gol del portero
                {OptaEventType.GOAL_SCORED_BY_DEFENDER.code, 80},    // gol del defensa
                {OptaEventType.GOAL_SCORED_BY_MIDFIELDER.code, 60},    // gol del medio
                {OptaEventType.GOAL_SCORED_BY_FORWARD.code, 40},    // gol del delantero
                {OptaEventType.OWN_GOAL.code, -10},  // gol en contra
                {OptaEventType.YELLOW_CARD.code, -30},    // tarjeta amarilla
                {OptaEventType.PUNCH.code, 10},     // despeje puños
                {OptaEventType.DISPOSSESSED.code, -10},    // perdida de balon
                {OptaEventType.ERROR.code, -20},    // perdida de balon
                {OptaEventType.CAUGHT_OFFSIDE.code, -5},     // fuera de juego
                {OptaEventType.FOUL_COMMITTED.code, -5},   // falta infligida
                {OptaEventType.RED_CARD.code, -100}, // tarjeta roja
                {OptaEventType.PENALTY_COMMITTED.code, -30},  // penalty infligido
                {OptaEventType.PENALTY_FAILED.code, -30},  // penalty fallado
                {OptaEventType.GOALKEEPER_SAVES_PENALTY.code, 30},   // penalty parado por el portero
                {OptaEventType.CLEAN_SHEET.code, 40},   // clean sheet
                {OptaEventType.GOAL_CONCEDED.code, -10},  // Gol al defensa
        };
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

        Iterable<TemplateSoccerTeam> randomTeamA = getRandomDocument(Model.templateSoccerTeams()).as(TemplateSoccerTeam.class);
        TemplateSoccerTeam teamA = (randomTeamA.iterator().hasNext()) ? randomTeamA.iterator().next() : null;

        Iterable<TemplateSoccerTeam> randomTeamB = getRandomDocument(Model.templateSoccerTeams()).as(TemplateSoccerTeam.class);
        TemplateSoccerTeam teamB = (randomTeamB.iterator().hasNext()) ? randomTeamB.iterator().next() : null;

        return (teamA != null && teamB != null)
                ? createTemplateMatchEvent(teamA.name, teamB.name, dateTime)
                : null;
    }

    /**
     * Obtener un elemento aleatorio de una coleccion de MongoDB
     * IMPORTANTE: Muy lento
     * @param collection MongoCollection de la que obtener el elemento
     * @return Un elemento aleatorio
     */
    static private Find getRandomDocument(MongoCollection collection) {
        long count = collection.count();
        int rand = (int) Math.floor(Math.random() * count);
        return collection.find().limit(1).skip(rand);
    }

    static private TemplateMatchEvent createTemplateMatchEvent(final String nameTeamA, final String nameTeamB, final DateTime dateTime) {
        TemplateSoccerTeam teamA = Model.templateSoccerTeams().findOne("{ name: '#' }", nameTeamA).as(TemplateSoccerTeam.class);
        TemplateSoccerTeam teamB = Model.templateSoccerTeams().findOne("{ name: '#' }", nameTeamB).as(TemplateSoccerTeam.class);

        return TemplateMatchEvent.create(teamA, teamB, dateTime.toDate());
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
                Model.contests().insert(contest);
            }
        }
    }

    static int optaPlayerId = 0;
    static int optaTeamId = 0;
}
