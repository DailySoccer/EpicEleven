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
        createCompetition("4", "IG_WC", "World Cup", "2013");
        createCompetition("5", "EU_CL", "Champions League", "2014");
        createCompetition("23", "ES_PL", "Spanish La Liga", "2014");
    }

    static private void createCompetition(String competitionId, String competitionCode, String competitionName, String seasonId) {
        OptaCompetition optaCompetition = OptaCompetition.findOne(competitionId, seasonId);
        if (optaCompetition == null) {
            Model.optaCompetitions().insert(new OptaCompetition(competitionId, competitionCode, competitionName, seasonId));
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
