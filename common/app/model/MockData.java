package model;

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

        createUser("Test",      "Test",         "Test",         "test@test.com", "");
    }

    static private void createUser(String firstName, String lastName, String nickName, String email, String password) {
        Model.users().insert(new User(firstName, lastName, nickName, email, password));
    }

    public static void createPointsTranslation() {
        int[][] pointsTable = {
                {OptaEventType.PASS_SUCCESSFUL._code, 2},               // pase bien hecho
                {OptaEventType.TAKE_ON._code, 10},                      // regate
                {OptaEventType.FOUL_RECEIVED._code, 10},                // falta recibida
                {OptaEventType.TACKLE_EFFECTIVE._code, 15},             // recuperacion/entrada con posesion
                {OptaEventType.INTERCEPTION._code, 15},                 // intercepcion
                {OptaEventType.SAVE._code, 10},                         // parada
                {OptaEventType.CLAIM._code, 10},                        // captura balon
                {OptaEventType.CLEARANCE._code, 10},                    // parada
                {OptaEventType.MISS._code, 15},                         // tiro a puerta
                {OptaEventType.POST._code, 15},                         // tiro a puerta
                {OptaEventType.ATTEMPT_SAVED._code, 15},                // tiro a puerta
                {OptaEventType.ASSIST._code, 20},                       // asistencia
                //{16, 100},                                            // gol
                {OptaEventType.GOAL_SCORED_BY_GOALKEEPER._code, 100},   // gol del portero
                {OptaEventType.GOAL_SCORED_BY_DEFENDER._code, 80},      // gol del defensa
                {OptaEventType.GOAL_SCORED_BY_MIDFIELDER._code, 60},    // gol del medio
                {OptaEventType.GOAL_SCORED_BY_FORWARD._code, 40},       // gol del delantero
                {OptaEventType.OWN_GOAL._code, -10},                    // gol en contra
                {OptaEventType.YELLOW_CARD._code, -30},                 // tarjeta amarilla
                {OptaEventType.PUNCH._code, 10},                        // despeje puños
                {OptaEventType.PASS_UNSUCCESSFUL._code, -5},            // perdida de balon, pase perdido
                {OptaEventType.DISPOSSESSED._code, -5},                 // perdida de balon
                {OptaEventType.ERROR._code, -20},                       // perdida de balon
                {OptaEventType.CAUGHT_OFFSIDE._code, -5},               // fuera de juego
                {OptaEventType.FOUL_COMMITTED._code, -5},               // falta infligida
                {OptaEventType.RED_CARD._code, -100},                   // tarjeta roja
                {OptaEventType.PENALTY_COMMITTED._code, -30},           // penalty infligido
                {OptaEventType.PENALTY_FAILED._code, -30},              // penalty fallado
                {OptaEventType.GOALKEEPER_SAVES_PENALTY._code, 30},     // penalty parado por el portero
                {OptaEventType.CLEAN_SHEET._code, 40},                  // clean sheet
                {OptaEventType.GOAL_CONCEDED._code, -10},               // Gol al defensa
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

            contest.contestEntries.add(new ContestEntry(user.userId, contest.contestId, soccerIds));
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
