package controllers;

import model.*;
import model.opta.*;
import com.fasterxml.jackson.databind.JsonNode;
import org.bson.types.ObjectId;
import java.util.Date;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import play.Logger;
import play.mvc.Controller;
import play.mvc.Result;
import play.data.Form;
import play.data.validation.Constraints;
import play.data.validation.ValidationError;

import static play.data.Form.form;

import utils.*;
import java.util.List;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;

import views.admin.formData.*;

public class AdminController extends Controller {
    public static Result resetDB() {
        Model.resetDB();
        return ok(views.html.admin.dashboard.render(FlashMessage.success("Reset DB: OK")));
    }

    public static Result createMockDataDB() {
        Model.resetDB();
        MockData.ensureMockDataAll();
        return ok(views.html.admin.dashboard.render(FlashMessage.success("Reset DB with Mock Data")));
    }

    public static Result resetContests() {
        Model.resetContests();
        return ok(views.html.admin.dashboard.render(FlashMessage.success("Reset Contests: OK")));
    }

    public static Result createMockDataContests() {
        Model.resetContests();
        MockData.ensureMockDataContests();
        return ok(views.html.admin.dashboard.render(FlashMessage.success("Reset Contests with Mock data: OK")));
    }

    public static Result importTeamsAndSoccers() {
        Model.resetContests();
        Model.importTeamsAndSoccersFromOptaDB();
        return ok(views.html.admin.dashboard.render(FlashMessage.success("Import Teams & Soccers: OK")));
    }

    public static Result importMatchEvents() {
        Model.importMatchEventsFromOptaDB();
        return ok(views.html.admin.dashboard.render(FlashMessage.success("Import Match Events: OK")));
    }

    public static Result dashBoard() {
        return ok(views.html.admin.dashboard.render(null));
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

    public static Result createPointsTranslation(){
        int[][] pointsTable = {{1, 2},
                {3, 10},
                {4, 15},
                {7, 15},
                {8, 15},
                {10, 20},
                {11, 20},
                {12, 10},
                {13, 20},
                {14, 20},
                {15, 20},
                {16, 100},
                {17, -50},
                {41, 10},
                {50, -20},
                {51, -20},
                {72, -5},
                {1004, -5},
                {1017, -200}};
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
        return redirect(routes.AdminController.pointsTranslations());
    }

    public static Result pointsTranslations() {
        Iterable<PointsTranslation> pointsTranslationsResults = Model.pointsTranslation().find().as(PointsTranslation.class);
        List<PointsTranslation> pointsTranslationsList = ListUtils.listFromIterator(pointsTranslationsResults.iterator());

        return ok(views.html.admin.points_translation_list.render(pointsTranslationsList));
    }

    public static Result addContestEntry() {
        Form<ContestEntryForm> contestEntryForm = Form.form(ContestEntryForm.class);
        return ok(views.html.admin.contest_entry_add.render(contestEntryForm));
    }

    public static Result submitContestEntry() {
        Form<ContestEntryForm> contestEntryForm = form(ContestEntryForm.class).bindFromRequest();
        if (contestEntryForm.hasErrors()) {
            return badRequest(views.html.admin.contest_entry_add.render(contestEntryForm));
        }

        ContestEntryForm params = contestEntryForm.get();
        Logger.info("UserId({}) Contest({})Goalkeeper({}) Defenses({}, {}, {}, {}) Middles({}, {}, {}, {}), Forwards({}, {})",
                params.userId, params.contestId,
                params.goalkeeper,
                params.defense1, params.defense2, params.defense3, params.defense4,
                params.middle1, params.middle2, params.middle3, params.middle4,
                params.forward1, params.forward2);

        boolean success = ContestController.createContestEntryFromOptaIds(params.userId, params.contestId, params.getTeam());
        if ( !success ) {
            return badRequest(views.html.admin.contest_entry_add.render(contestEntryForm));
        }

        return redirect(routes.AdminController.contestEntries());
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

    public static Result instantiateContests() {
        //TODO: En que fecha tendriamos que generar los contests?
        DateTime currentCreationDay =  new DateTime(2014, 10, 14, 12, 0, DateTimeZone.UTC);
        MockData.instantiateContests(currentCreationDay);

        return redirect(routes.AdminController.contests());
    }

    public static Result templateContests() {
        Iterable<TemplateContest> templateContestResults = Model.templateContests().find().as(TemplateContest.class);
        List<TemplateContest> templateContestList = ListUtils.listFromIterator(templateContestResults.iterator());

        return ok(views.html.admin.template_contest_list.render(templateContestList));
    }

    public static Result templateContest(String templateContestId) {
        return TODO;
    }

    public static Result createTemplateContest() {
        //TODO: En que fecha tendriamos que generar el contest?
        DateTime currentCreationDay =  new DateTime(2014, 10, 14, 12, 0, DateTimeZone.UTC);

        MockData.createTemplateContest(currentCreationDay);

        return redirect(routes.AdminController.templateContests());
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

    public static Result createTemplateMatchEvent() {
        //TODO: En que fecha tendriamos que generar el partido?
        DateTime currentCreationDay =  new DateTime(2014, 10, 14, 12, 0, DateTimeZone.UTC);

        MockData.createTemplateMatchEvent(currentCreationDay);

        return redirect(routes.AdminController.templateMatchEvents());
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

    public static Result optaMatchEvents() {
        Iterable<OptaMatchEvent> optaMatchEventResults = Model.optaMatchEvents().find().as(OptaMatchEvent.class);
        List<OptaMatchEvent> optaMatchEventList = ListUtils.listFromIterator(optaMatchEventResults.iterator());

        return ok(views.html.admin.opta_match_event_list.render(optaMatchEventList));
    }
}