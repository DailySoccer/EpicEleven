package controllers;

import model.*;
import model.opta.*;
import org.bson.types.ObjectId;
import play.mvc.Controller;
import play.mvc.Result;

import utils.*;
import java.util.List;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;

public class AdminController extends Controller {
    public static Result resetDB() {
        Model.resetDB();
        return ok(views.html.admin.dashboard.render("> Reset DB: OK"));
    }

    public static Result createMockDataDB() {
        Model.resetDB();
        MockData.ensureMockDataAll();
        return ok(views.html.admin.dashboard.render("> Reset DB with Mock Data"));
    }

    public static Result resetContests() {
        Model.resetContests();
        return ok(views.html.admin.dashboard.render("> Reset Contests: OK"));
    }

    public static Result createMockDataContests() {
        Model.resetContests();
        MockData.ensureMockDataContests();
        return ok(views.html.admin.dashboard.render("> Reset Contests with Mock data: OK"));
    }

    public static Result importTeamsAndSoccers() {
        Model.resetContests();
        Model.importTeamsAndSoccersFromOptaDB();
        return ok(views.html.admin.dashboard.render("> Import Teams & Soccers: OK"));
    }

    public static Result importMatchEvents() {
        return ok(views.html.admin.dashboard.render("> TODO: Action not implemented yet."));
    }

    public static Result dashBoard() {
        return ok(views.html.admin.dashboard.render(""));
    }

    public static Result lobby() {
        Iterable<Contest> contestsResults = Model.contests().find().as(Contest.class);
        List<Contest> contestList = ListUtils.listFromIterator(contestsResults.iterator());

        HashMap<ObjectId, TemplateContest> templateContestMap = getTemplateContestsFromList(contestList);

        return ok(views.html.admin.lobby.render(contestList, templateContestMap));
    }

    public static Result users() {
        Iterable<User> userResults = Model.users().find().as(User.class);
        List<User> userList = ListUtils.listFromIterator(userResults.iterator());

        return ok(views.html.admin.user_list.render(userList));
    }

    public static Result liveMatchEvents() {
        Iterable<LiveMatchEvent> liveMatchEventResults = Model.liveMatchEvents().find().as(LiveMatchEvent.class);
        List<LiveMatchEvent> liveMatchEventList = ListUtils.listFromIterator(liveMatchEventResults.iterator());

        return ok(views.html.admin.live_match_event_list.render(liveMatchEventList));
    }

    public static Result contestEntries() {
        Iterable<ContestEntry> contestEntryResults = Model.contestEntries().find().as(ContestEntry.class);
        List<ContestEntry> contestEntryList = ListUtils.listFromIterator(contestEntryResults.iterator());

        return ok(views.html.admin.contest_entry_list.render(contestEntryList));
    }

    public static Result contests() {
        Iterable<Contest> contestsResults = Model.contests().find().as(Contest.class);
        List<Contest> contestList = ListUtils.listFromIterator(contestsResults.iterator());

        return ok(views.html.admin.contest_list.render(contestList));
    }

    public static Result contest(String contestId) {
        Contest contest = Model.contests().findOne("{ _id : # }", new ObjectId(contestId)).as(Contest.class);
        HashMap<Integer, String> map = new HashMap<>();
        map.put(0, "hola");
        map.put(1, "adios");
        return ok(views.html.admin.contest.render(contest, map));
    }

    public static Result templateContests() {
        Iterable<TemplateContest> templateContestResults = Model.contests().find().as(TemplateContest.class);
        List<TemplateContest> templateContestList = ListUtils.listFromIterator(templateContestResults.iterator());

        return ok(views.html.admin.template_contest_list.render(templateContestList));
    }

    public static Result templateContest(String templateContestId) {
        return TODO;
    }

    public static Result templateMatchEvents() {
        Iterable<TemplateMatchEvent> soccerMatchEventResults = Model.templateMatchEvents().find().as(TemplateMatchEvent.class);
        List<TemplateMatchEvent> matchEventList = ListUtils.listFromIterator(soccerMatchEventResults.iterator());

        return ok(views.html.admin.template_match_event_list.render(matchEventList));
    }

    public static Result templateMatchEvent(String templateMatchEventId) {
        TemplateMatchEvent templateMatchEvent = Model.templateMatchEvents().findOne("{ _id : # }",
                new ObjectId(templateMatchEventId)).as(TemplateMatchEvent.class);
        return ok(views.html.admin.template_match_event.render(templateMatchEvent));
    }

    public static Result templateSoccerTeams() {
        Iterable<TemplateSoccerTeam> soccerTeamResults = Model.templateSoccerTeams().find().as(TemplateSoccerTeam.class);
        List<TemplateSoccerTeam> soccerTeamList = ListUtils.listFromIterator(soccerTeamResults.iterator());

        return ok(views.html.admin.template_soccer_team_list.render(soccerTeamList));
    }

    public static Result templateSoccerTeam(String templateSoccerTeamId) {
        TemplateSoccerTeam templateSoccerTeam = Model.templateSoccerTeams().findOne("{ _id : # }",
                new ObjectId(templateSoccerTeamId)).as(TemplateSoccerTeam.class);
        Iterable<TemplateSoccerPlayer> soccerPlayerResults = Model.templateSoccerPlayers().find("{ templateTeamId: # }",
                new ObjectId(templateSoccerTeamId)).as(TemplateSoccerPlayer.class);
        List<TemplateSoccerPlayer> soccerPlayerList = ListUtils.listFromIterator(soccerPlayerResults.iterator());
        return ok(views.html.admin.template_soccer_team.render(templateSoccerTeam, soccerPlayerList));
    }

    public static Result templateSoccerPlayers() {
        Iterable<TemplateSoccerPlayer> soccerPlayerResults = Model.templateSoccerPlayers().find().as(TemplateSoccerPlayer.class);
        List<TemplateSoccerPlayer> soccerPlayerList = ListUtils.listFromIterator(soccerPlayerResults.iterator());

        return ok(views.html.admin.template_soccer_player_list.render(soccerPlayerList));
    }

    public static HashMap<ObjectId, TemplateContest> getTemplateContestsFromList(List<Contest> contestList) {
        // Obtener la lista de Ids de los TemplateContests
        ArrayList<ObjectId> idsList = new ArrayList<>();
        for (Contest contest : contestList) {
            idsList.add(contest.templateContestId);
        }

        // Obtenemos las lista de TemplateContest de todos los Ids
        Iterable<TemplateContest> templateContestResults = Model.findTemplateContestsFromIds("_id", idsList);

        // Convertirlo a map
        HashMap<ObjectId, TemplateContest> ret = new HashMap<>();

        for (TemplateContest template : templateContestResults) {
            ret.put(template.templateContestId, template);
        }

        return ret;
    }

    public static Result optaSoccerPlayers() {
        Iterable<OptaPlayer> optaPlayerResults = Model.optaPlayers().find().as(OptaPlayer.class);
        List<OptaPlayer> optaPlayerList = ListUtils.listFromIterator(optaPlayerResults.iterator());

        return ok(views.html.admin.opta_soccer_player_list.render(optaPlayerList));
    }

    public static Result optaSoccerTeams() {
        Iterable<OptaTeam> optaTeamResults = Model.optaTeams().find().as(OptaTeam.class);
        List<OptaTeam> optaTeamList = ListUtils.listFromIterator(optaTeamResults.iterator());

        return ok(views.html.admin.opta_soccer_team_list.render(optaTeamList));
    }
}