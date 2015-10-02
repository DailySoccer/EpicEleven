package model;

import model.jobs.EnterContestJob;
import model.opta.OptaCompetition;
import org.bson.types.ObjectId;
import org.joda.money.Money;
import utils.ListUtils;
import utils.MoneyUtils;

import java.util.ArrayList;
import java.util.List;

public final class MockData {


    static public void ensureMockDataUsers() {
        createUser("Fran",      "Galvez",       "FranTest",         "fran@test.com", "");
        createUser("Victor",    "Mendiluce",    "ZincoontrinTest",  "vmendi@test.com", "");
        createUser("Santiago",  "Gonzalez",     "FlacoTest",        "flaco@test.com", "");
        createUser("Santiago",  "Revelo",       "ReveloTest",       "revelo@test.com", "");
        createUser("Javier",    "Lajara",       "JaviTest",         "javi@test.com", "");
        createUser("Ximo",      "Martinez",     "XimoTest",         "ximo@test.com", "");
        createUser("Santiago",  "R. Bedate",    "NeoTest",          "neo@test.com", "");
        createUser("Federico",  "Mon",          "FedeTest",         "fede@test.com", "");
        createUser("Jesús",     "Tapial",       "MachusTest",       "machus@test.com", "");
        createUser("Antonio",   "Galvez",       "AntonioTest",      "antonio@test.com", "");
        createUser("Belen",     "Cao",          "BelenTest",        "belen@test.com", "");
        createUser("Goyo",      "Iniesta",      "GoyoTest",         "goyo@test.com", "");
        createUser("María",     "Aguilera",     "MariaTest",        "maria@test.com", "");


        createUser("Test",      "Test",         "Test",             "test@test.com", "");
    }

    static private void createUser(String firstName, String lastName, String nickName, String email, String password) {
        Model.users().insert(new User(firstName, lastName, nickName, email));
    }

    public static void ensureCompetitions() {
        createCompetition("4",  "IG_WC", "World Cup", "2013");
        createCompetition("5",  "EU_CL", "Champions League", "2014");
        createCompetition("6",  "EU_UC", "UEFA Europa League", "2014");
        createCompetition("8",  "EN_PR", "English Barclays Premier League", "2014");
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

        List<TemplateSoccerPlayer> goalKeepers = new ArrayList<>();
        List<TemplateSoccerPlayer> defenses = new ArrayList<>();
        List<TemplateSoccerPlayer> middles = new ArrayList<>();
        List<TemplateSoccerPlayer> forwards = new ArrayList<>();

        for (TemplateMatchEvent templateMatchEvent : TemplateContest.findOne(contest.templateContestId).getTemplateMatchEvents()) {
            List<TemplateSoccerPlayer> soccerPlayers = TemplateSoccerPlayer.findAllFromInstances(contest.instanceSoccerPlayers);

            goalKeepers.addAll(filterSoccerPlayersFromFieldPos(soccerPlayers, FieldPos.GOALKEEPER));
            defenses.addAll(filterSoccerPlayersFromFieldPos(soccerPlayers, FieldPos.DEFENSE));
            middles.addAll(filterSoccerPlayersFromFieldPos(soccerPlayers, FieldPos.MIDDLE));
            forwards.addAll(filterSoccerPlayersFromFieldPos(soccerPlayers, FieldPos.FORWARD));
        }

        // Todos los usuarios excepto el "Test" y los "Bots"
        List<User> users = ListUtils.asList(Model.users().find("{email: {$regex: #}, nickName: {$ne: 'Test'}, firstName: {$ne: 'Bototron'}}", "@test.com").as(User.class));
        for (int i=0; i<size && i<users.size(); i++) {
            User user = users.get(i);

            if (contest.entryFee.isPositive()) {
                // El usuario tiene que tener el dinero suficiente para entrar en el contest
                if (!user.hasMoney(contest.entryFee)) {
                    continue;
                }
            }

            List<ObjectId> soccerIds = new ArrayList<>();

            for (TemplateSoccerPlayer soccer : ListUtils.randomElements(goalKeepers, 1))
                soccerIds.add(soccer.templateSoccerPlayerId);

            for (TemplateSoccerPlayer soccer : ListUtils.randomElements(defenses, 4))
                soccerIds.add(soccer.templateSoccerPlayerId);

            for (TemplateSoccerPlayer soccer : ListUtils.randomElements(middles, 4))
                soccerIds.add(soccer.templateSoccerPlayerId);

            for (TemplateSoccerPlayer soccer : ListUtils.randomElements(forwards, 2))
                soccerIds.add(soccer.templateSoccerPlayerId);

            EnterContestJob.create(user.userId, contest.contestId, soccerIds);
        }

    }

    private static List<TemplateSoccerPlayer> filterSoccerPlayersFromFieldPos(List<TemplateSoccerPlayer> soccerPlayers, FieldPos fieldPos) {
        List<TemplateSoccerPlayer> soccers = new ArrayList<>();

        for (TemplateSoccerPlayer soccer : soccerPlayers) {
            if (soccer.fieldPos.equals(fieldPos)) {
                soccers.add(soccer);
            }
        }

        return soccers;
    }
}
