package model;

import model.opta.OptaCompetition;
import model.opta.OptaEventType;
import org.bson.types.ObjectId;
import utils.ListUtils;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;


public final class MockData {


    static public void ensureMockDataUsers() {
        createUser("Fran",      "Galvez",       "Fran",         "fran@test.com", "");
        createUser("Victor",    "Mendiluce",    "Zincoontrin",  "vmendi@test.com", "");
        createUser("Santiago",  "Gonzalez",     "Flaco",        "flaco@test.com", "");
        createUser("Santiago",  "Revelo",       "Revelo",       "revelo@test.com", "");
        createUser("Javier",    "Lajara",       "Javi",         "javi@test.com", "");
        createUser("Ximo",      "Martinez",     "Ximo",         "ximo@test.com", "");
        createUser("Santiago",  "R. Bedate",    "Neo",          "neo@test.com", "");
        createUser("Federico",  "Mon",          "Fede",         "fede@test.com", "");
        createUser("Jesús",     "Tapial",       "Machus",       "machus@test.com", "");
        createUser("Antonio",   "Galvez",       "Antonio",      "antonio@test.com", "");
        createUser("Belen",     "Cao",          "Belen",        "belen@test.com", "");
        createUser("Goyo",      "Iniesta",      "Goyo",         "goyo@test.com", "");

        createUser("Test",      "Test",         "Test",         "test@test.com", "");
    }

    static private void createUser(String firstName, String lastName, String nickName, String email, String password) {
        Model.users().insert(new User(firstName, lastName, nickName, email, password));
    }

    public static void ensureCompetitions() {
        createCompetition("4", "IG_WC", "World Cup");
        createCompetition("5", "EU_CL", "Champions League");
        createCompetition("23", "ES_PL", "Spanish La Liga");
    }

    static private void createCompetition(String competitionId, String competitionCode, String competitionName) {
        OptaCompetition optaCompetition = OptaCompetition.findOne(competitionId);
        if (optaCompetition == null) {
            Model.optaCompetitions().insert(new OptaCompetition(competitionId, competitionCode, competitionName));
        }
    }

    public static void createPointsTranslation() {
        int[][] pointsTable = {
                {OptaEventType.PASS_SUCCESSFUL.code, 1},               // pase bien hecho
                {OptaEventType.TAKE_ON.code, 5},                       // regate
                {OptaEventType.FOUL_RECEIVED.code, 3},                 // falta recibida
                {OptaEventType.TACKLE_EFFECTIVE.code, 10},             // recuperacion/entrada con posesion
                {OptaEventType.INTERCEPTION.code, 3},                  // intercepcion
                {OptaEventType.SAVE.code, 10},                         // parada
                {OptaEventType.CLAIM.code, 10},                        // captura balon
                {OptaEventType.CLEARANCE.code, 5},                     // parada
                {OptaEventType.MISS.code, 5},                          // tiro a puerta
                {OptaEventType.POST.code, 5},                          // tiro a puerta
                {OptaEventType.ATTEMPT_SAVED.code, 5},                 // tiro a puerta
                {OptaEventType.ASSIST.code, 10},                       // asistencia
                //{16, 100},                                           // gol
                {OptaEventType.GOAL_SCORED_BY_GOALKEEPER.code, 75},    // gol del portero
                {OptaEventType.GOAL_SCORED_BY_DEFENDER.code, 65},      // gol del defensa
                {OptaEventType.GOAL_SCORED_BY_MIDFIELDER.code, 50},    // gol del medio
                {OptaEventType.GOAL_SCORED_BY_FORWARD.code, 40},       // gol del delantero
                {OptaEventType.OWN_GOAL.code, -10},                    // gol en contra
                {OptaEventType.YELLOW_CARD.code, -20},                 // tarjeta amarilla
                {OptaEventType.SECOND_YELLOW_CARD.code, -20},          // tarjeta amarilla (segunda)
                {OptaEventType.PUNCH.code, 5},                        // despeje puños
                {OptaEventType.PASS_UNSUCCESSFUL.code, -2},            // perdida de balon, pase perdido
                {OptaEventType.DISPOSSESSED.code, -5},                 // perdida de balon
                {OptaEventType.ERROR.code, -20},                       // perdida de balon
                {OptaEventType.CAUGHT_OFFSIDE.code, -5},               // fuera de juego
                {OptaEventType.FOUL_COMMITTED.code, -5},               // falta infligida
                {OptaEventType.RED_CARD.code, -50},                    // tarjeta roja
                {OptaEventType.PENALTY_COMMITTED.code, -20},           // penalty infligido
                {OptaEventType.PENALTY_FAILED.code, -30},              // penalty fallado
                {OptaEventType.GOALKEEPER_SAVES_PENALTY.code, 30},     // penalty parado por el portero
                {OptaEventType.CLEAN_SHEET.code, 30},                  // clean sheet
                {OptaEventType.GOAL_CONCEDED.code, -10},               // Gol al defensa
        };

        for (int i = 0; i < pointsTable.length; i++) {
            PointsTranslation myPointsTranslation = new PointsTranslation();

            myPointsTranslation.eventTypeId = pointsTable[i][0];

            PointsTranslation pointsTranslation = Model.pointsTranslation().findOne("{eventTypeId: #}", myPointsTranslation.eventTypeId).as(PointsTranslation.class);

            if (pointsTranslation == null) {
                myPointsTranslation.unixtimestamp = 0L;
                myPointsTranslation.timestamp = new Date(myPointsTranslation.unixtimestamp);
                myPointsTranslation.points = pointsTable[i][1];
                myPointsTranslation.createdAt = GlobalDate.getCurrentDate();
                Model.pointsTranslation().insert(myPointsTranslation);
            }
        }
    }

    public static void addContestEntries(Contest contest, int size) {
        // Si no tiene contestId (aún no ha sido insertado en una collection), lo generamos
        if (contest.contestId == null) {
            contest.contestId = new ObjectId();
        }

        List<SoccerPlayer> goalKeepers = new ArrayList<>();
        List<SoccerPlayer> defenses = new ArrayList<>();
        List<SoccerPlayer> middles = new ArrayList<>();
        List<SoccerPlayer> forwards = new ArrayList<>();

        for (MatchEvent matchEvent : TemplateContest.findOne(contest.templateContestId).getMatchEvents()) {
            goalKeepers.addAll(filterSoccerPlayersFromFieldPos(matchEvent.soccerTeamA, FieldPos.GOALKEEPER));
            goalKeepers.addAll(filterSoccerPlayersFromFieldPos(matchEvent.soccerTeamB, FieldPos.GOALKEEPER));

            defenses.addAll(filterSoccerPlayersFromFieldPos(matchEvent.soccerTeamA, FieldPos.DEFENSE));
            defenses.addAll(filterSoccerPlayersFromFieldPos(matchEvent.soccerTeamB, FieldPos.DEFENSE));

            middles.addAll(filterSoccerPlayersFromFieldPos(matchEvent.soccerTeamA, FieldPos.MIDDLE));
            middles.addAll(filterSoccerPlayersFromFieldPos(matchEvent.soccerTeamB, FieldPos.MIDDLE));

            forwards.addAll(filterSoccerPlayersFromFieldPos(matchEvent.soccerTeamA, FieldPos.FORWARD));
            forwards.addAll(filterSoccerPlayersFromFieldPos(matchEvent.soccerTeamB, FieldPos.FORWARD));
        }

        // Todos los usuarios excepto el "Test"
        List<User> users = ListUtils.asList(Model.users().find("{nickName: {$ne: 'Test'}}").as(User.class));
        for (int i=0; i<size && i<users.size(); i++) {
            User user = users.get(i);

            List<ObjectId> soccerIds = new ArrayList<>();

            for (SoccerPlayer soccer : ListUtils.randomElements(goalKeepers, 1))
                soccerIds.add(soccer.templateSoccerPlayerId);

            for (SoccerPlayer soccer : ListUtils.randomElements(defenses, 4))
                soccerIds.add(soccer.templateSoccerPlayerId);

            for (SoccerPlayer soccer : ListUtils.randomElements(middles, 4))
                soccerIds.add(soccer.templateSoccerPlayerId);

            for (SoccerPlayer soccer : ListUtils.randomElements(forwards, 2))
                soccerIds.add(soccer.templateSoccerPlayerId);

            contest.contestEntries.add(new ContestEntry(user.userId, soccerIds));
        }

    }

    private static List<SoccerPlayer> filterSoccerPlayersFromFieldPos(SoccerTeam team, FieldPos fieldPos) {
        List<SoccerPlayer> soccers = new ArrayList<>();

        for (SoccerPlayer soccer : team.soccerPlayers) {
            if (soccer.fieldPos.equals(fieldPos)) {
                soccers.add(soccer);
            }
        }

        return soccers;
    }
}
