package controllers.admin;

import model.*;
import model.opta.OptaMatchEvent;
import model.opta.OptaPlayer;
import model.opta.OptaTeam;
import model.opta.OptaEvent;
import org.bson.types.ObjectId;
import java.util.Date;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import play.Logger;
import play.data.Form;
import play.mvc.Controller;
import play.mvc.Result;
import utils.ListUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import static play.data.Form.form;


public class AdminController extends Controller {

    public static Result dashBoard() {
        return ok(views.html.dashboard.render());
    }

    public static Result resetDB() {
        Model.resetDB();

        FlashMessage.success("Reset DB: OK");
        return ok(views.html.dashboard.render());
    }

    public static Result createMockDataDB() {
        Model.resetDB();
        MockData.ensureMockDataAll();

        FlashMessage.success("Reset DB with Mock Data");
        return ok(views.html.dashboard.render());
    }

    public static Result resetContests() {
        Model.resetContests();

        FlashMessage.success("Reset Contests: OK");
        return ok(views.html.dashboard.render());
    }

    public static Result createMockDataContests() {
        Model.resetContests();
        MockData.ensureMockDataContests();

        FlashMessage.success("Reset Contests with Mock data: OK");
        return ok(views.html.dashboard.render());
    }

    public static Result importTeamsAndSoccers() {
        Model.resetContests();
        Model.importTeamsAndSoccersFromOptaDB();

        FlashMessage.success("Import Teams & Soccers: OK");
        return ok(views.html.dashboard.render());
    }

    public static Result importMatchEvents() {
        Model.importMatchEventsFromOptaDB();

        FlashMessage.success("Import Match Events: OK");
        return ok(views.html.dashboard.render());
    }

    public static Result lobby() {
        Iterable<Contest> contestsResults = Model.contests().find().as(Contest.class);
        List<Contest> contestList = ListUtils.asList(contestsResults);

        HashMap<ObjectId, TemplateContest> templateContestMap = getTemplateContestsFromList(contestList);

        return ok(views.html.lobby.render(contestList, templateContestMap));
    }

    public static Result users() {
        Iterable<User> userResults = Model.users().find().as(User.class);
        List<User> userList = ListUtils.asList(userResults);

        return ok(views.html.user_list.render(userList));
    }

    public static Result showPlayerFantasyPointsInContest(String contestId, String playerId) {
        List<OptaEvent> optaEventList = new ArrayList<>();

        TemplateSoccerPlayer templateSoccerPlayer = Model.templateSoccerPlayer( new ObjectId(playerId) );
        Contest contest = Model.contest( new ObjectId(contestId) );
        TemplateContest templateContest = Model.templateContest(contest.templateContestId);
        List<TemplateMatchEvent> templateMatchEvents = Model.templateMatchEvents(templateContest);

        for (TemplateMatchEvent templateMatchEvent : templateMatchEvents) {
            optaEventList.addAll(Model.optaEvents(templateMatchEvent.optaMatchEventId, templateSoccerPlayer.optaPlayerId));
        }

        return ok(views.html.player_fantasy_points.render(templateSoccerPlayer, optaEventList));
    }

    public static Result showPlayerFantasyPointsInMatchEvent(String templateMatchEventId, String playerId) {
        List<OptaEvent> optaEventList = new ArrayList<>();

        TemplateSoccerPlayer templateSoccerPlayer = Model.templateSoccerPlayer( new ObjectId(playerId) );
        TemplateMatchEvent templateMatchEvent = Model.templateMatchEvent( new ObjectId(templateMatchEventId) );

        optaEventList.addAll(Model.optaEvents(templateMatchEvent.optaMatchEventId, templateSoccerPlayer.optaPlayerId));

        return ok(views.html.player_fantasy_points.render(templateSoccerPlayer, optaEventList));
    }

    public static Result updateLive() {
        // Obtenemos la lista de TemplateMatchEvents
        Iterable<TemplateMatchEvent> templateMatchEventsResults = Model.templateMatchEvents().find().as(TemplateMatchEvent.class);

        // Existira un live Match Event por cada template Match Event
        for (TemplateMatchEvent templateMatchEvent : templateMatchEventsResults) {
            LiveMatchEvent liveMatchEvent = new LiveMatchEvent(templateMatchEvent);
            Model.liveMatchEvents().update("{templateMatchEventId: #}", templateMatchEvent.templateMatchEventId).upsert().with(liveMatchEvent);

            // Actualizar los fantasy points de cada live match event
            Model.updateLiveFantasyPoints(liveMatchEvent);
        }

        return redirect(routes.AdminController.liveMatchEvents());
    }

    public static Result updateContestEntriesLive() {
        updateLive();
        return redirect(routes.AdminController.liveContestEntries());
    }

    public static Result liveContestEntry(String contestEntryId) {
        ContestEntry contestEntry = Model.contestEntries().findOne("{ _id : # }", new ObjectId(contestEntryId)).as(ContestEntry.class);
        List<SoccerPlayer> soccer_players = Model.getSoccerPlayersInContestEntry(contestEntryId);
        return ok(views.html.live_contest_entry.render(contestEntry, soccer_players));
    }

    public static Result liveContestEntries() {
        Iterable<ContestEntry> contestEntryResults = Model.contestEntries().find().as(ContestEntry.class);
        List<ContestEntry> contestEntryList = ListUtils.asList(contestEntryResults);

        return ok(views.html.live_contest_entry_list.render(contestEntryList));
    }

    public static Result liveMatchEvent(String liveMatchEventId) {
        LiveMatchEvent liveMatchEvent = Model.liveMatchEvents().findOne("{ _id : # }",
                new ObjectId(liveMatchEventId)).as(LiveMatchEvent.class);
        return ok(views.html.live_match_event.render(liveMatchEvent));
    }

    public static Result liveMatchEvents() {
        Iterable<LiveMatchEvent> liveMatchEventResults = Model.liveMatchEvents().find().as(LiveMatchEvent.class);
        List<LiveMatchEvent> liveMatchEventList = ListUtils.asList(liveMatchEventResults);

        return ok(views.html.live_match_event_list.render(liveMatchEventList));
    }

    public static Result submitPointsTranslation() {
        Form<PointsTranslationForm> pointsTranslationForm = form(PointsTranslationForm.class).bindFromRequest();
        if (pointsTranslationForm.hasErrors()) {
            return badRequest(views.html.points_translation_add.render(pointsTranslationForm));
        }

        PointsTranslationForm params = pointsTranslationForm.get();

        boolean success = Model.createPointsTranslation(params.eventType, params.points);
        if ( !success ) {
            FlashMessage.warning("Points Translation invalid");
            return badRequest(views.html.points_translation_add.render(pointsTranslationForm));
        }

        Logger.info("Event Type ({}) = {} points", params.eventType, params.points);

        return redirect(routes.AdminController.pointsTranslations());
    }

    public static Result addPointsTranslation() {
        Form<PointsTranslationForm> pointsTranslationForm = Form.form(PointsTranslationForm.class);
        return ok(views.html.points_translation_add.render(pointsTranslationForm));
    }

    public static Result createPointsTranslation(){
        Model.pointsTranslation().remove();
        MockData.createPointsTranslation();
        return redirect(routes.AdminController.pointsTranslations());
    }

    public static Result pointsTranslations() {
        Iterable<PointsTranslation> pointsTranslationsResults = Model.pointsTranslation().find().as(PointsTranslation.class);
        List<PointsTranslation> pointsTranslationsList = ListUtils.asList(pointsTranslationsResults);

        return ok(views.html.points_translation_list.render(pointsTranslationsList));
    }

    public static Result addContestEntry() {
        Form<ContestEntryForm> contestEntryForm = Form.form(ContestEntryForm.class);
        return ok(views.html.contest_entry_add.render(contestEntryForm, null));
    }

    private static List<TemplateMatchEvent> getTemplateMatchEvents(String contestId) {
        // Pasar la lista de partidos
        Contest contest = Model.contests().findOne("{_id: #}", new ObjectId(contestId)).as(Contest.class);
        TemplateContest templateContest = Model.templateContests().findOne("{_id: #}", contest.templateContestId).as(TemplateContest.class);

        Iterable<TemplateMatchEvent> templateMatchEventsResults = Model.findTemplateMatchEventFromIds("_id", templateContest.templateMatchEventIds);
        return ListUtils.asList(templateMatchEventsResults);
    }

    public static Result submitContestEntry() {
        Form<ContestEntryForm> contestEntryForm = form(ContestEntryForm.class).bindFromRequest();
        if (contestEntryForm.hasErrors()) {
            String contestId = contestEntryForm.field("contestId").value();
            List<TemplateMatchEvent> templateMatchEvents = ObjectId.isValid(contestId) ? getTemplateMatchEvents(contestId) : null;
            return badRequest(views.html.contest_entry_add.render(contestEntryForm, templateMatchEvents));
        }

        ContestEntryForm params = contestEntryForm.get();

        boolean success = Model.createContestEntryFromOptaIds(params.userId, params.contestId, params.getTeam());
        if ( !success ) {
            FlashMessage.warning("Contest Entry invalid");
            String contestId = contestEntryForm.field("contestId").value();
            List<TemplateMatchEvent> templateMatchEvents = ObjectId.isValid(contestId) ? getTemplateMatchEvents(contestId) : null;
            return badRequest(views.html.contest_entry_add.render(contestEntryForm, templateMatchEvents));
        }

        Logger.info("UserId({}) Contest({}) Goalkeeper({}) Defenses({}, {}, {}, {}) Middles({}, {}, {}, {}), Forwards({}, {})",
                params.userId, params.contestId,
                params.goalkeeper,
                params.defense1, params.defense2, params.defense3, params.defense4,
                params.middle1, params.middle2, params.middle3, params.middle4,
                params.forward1, params.forward2);

        return redirect(routes.AdminController.contestEntries());
    }

    public static Result contestEntries() {
        Iterable<ContestEntry> contestEntryResults = Model.contestEntries().find().as(ContestEntry.class);
        List<ContestEntry> contestEntryList = ListUtils.asList(contestEntryResults);

        return ok(views.html.contest_entry_list.render(contestEntryList));
    }

    public static Result enterContestWithForm(Form<ContestEntryForm> contestEntryForm) {
        ContestEntryForm params = contestEntryForm.get();
        String contestId = params.contestId;

        return ok(views.html.contest_entry_add.render(contestEntryForm, getTemplateMatchEvents(contestId)));
    }

    public static Result enterContest(String contestId) {
        ContestEntryForm params = new ContestEntryForm();
        params.contestId = contestId;

        Form<ContestEntryForm> contestEntryForm = Form.form(ContestEntryForm.class).fill(params);
        return enterContestWithForm(contestEntryForm);

        /*
        // Pasar la lista de partidos
        Contest contest = Model.contests().findOne("{_id: #}", new ObjectId(contestId)).as(Contest.class);
        TemplateContest templateContest = Model.templateContests().findOne("{_id: #}", contest.templateContestId).as(TemplateContest.class);

        Iterable<TemplateMatchEvent> templateMatchEventsResults = Model.findTemplateMatchEventFromIds("_id", templateContest.templateMatchEventIds);
        List<TemplateMatchEvent> templateMatchEventsList = ListUtils.asList(templateMatchEventsResults);

        return ok(views.html.contest_entry_add.render(contestEntryForm, templateMatchEventsList));
        */
    }

    public static Result contests() {
        Iterable<Contest> contestsResults = Model.contests().find().as(Contest.class);
        List<Contest> contestList = ListUtils.asList(contestsResults);

        return ok(views.html.contest_list.render(contestList));
    }

    public static Result contest(String contestId) {
        Contest contest = Model.contests().findOne("{ _id : # }", new ObjectId(contestId)).as(Contest.class);
        HashMap<Integer, String> map = new HashMap<>();
        map.put(0, "hola");
        map.put(1, "adios");
        return ok(views.html.contest.render(contest, map));
    }

    public static Result instantiateContests() {
        Iterable<TemplateContest> templateContests = Model.templateContests().find().as(TemplateContest.class);
        for(TemplateContest template : templateContests) {
            instantiateTemplateContest(template);
        }

        return redirect(routes.AdminController.contests());
    }

    public static void instantiateTemplateContest(TemplateContest templateContest) {
        // Cuantas instancias tenemos creadas?
        long instances = Model.contests().count("{templateContestId: #}", templateContest.templateContestId);

        for(long i=instances; i<templateContest.minInstances; i++) {
            Contest contest = new Contest(templateContest);
            contest.maxUsers = 10;
            Model.contests().insert(contest);
        }
    }

    public static Result templateContests() {
        Iterable<TemplateContest> templateContestResults = Model.templateContests().find().as(TemplateContest.class);
        List<TemplateContest> templateContestList = ListUtils.asList(templateContestResults);

        return ok(views.html.template_contest_list.render(templateContestList));
    }

    public static Result submitTemplateContest() {
        Form<TemplateContestForm> templateContestForm = form(TemplateContestForm.class).bindFromRequest();
        if (templateContestForm.hasErrors()) {
            return badRequest(views.html.template_contest_add.render(templateContestForm, TemplateContestForm.matchEventsOptions()));
        }

        TemplateContestForm params = templateContestForm.get();

        TemplateContest templateContest = new TemplateContest();
        templateContest.name = params.name;
        templateContest.postName = params.postName;
        templateContest.minInstances = params.minInstances;
        templateContest.maxEntries = params.maxEntries;
        templateContest.salaryCap = params.salaryCap;
        templateContest.entryFee = params.entryFee;
        templateContest.prizeType = params.prizeType;

        Date startDate = null;
        templateContest.templateMatchEventIds = new ArrayList<>();
        for (String optaMatchEventId: params.templateMatchEvents) {
            TemplateMatchEvent templateMatchEvent = Model.templateMatchEvents().findOne(
                    "{optaMatchEventId: #}", optaMatchEventId).as(TemplateMatchEvent.class);
            templateContest.templateMatchEventIds.add(templateMatchEvent.templateMatchEventId);

            if (startDate == null || templateMatchEvent.startDate.before(startDate)) {
                startDate = templateMatchEvent.startDate;
            }
        }
        templateContest.startDate = startDate;

        for(String p: params.templateMatchEvents) {
            Logger.info("{}", p);
        }

        Model.templateContests().insert(templateContest);

        return redirect(routes.AdminController.templateContests());
    }

    public static Result addTemplateContest() {
        Form<TemplateContestForm> templateContestForm = Form.form(TemplateContestForm.class);
        return ok(views.html.template_contest_add.render(templateContestForm, TemplateContestForm.matchEventsOptions()));
    }

    public static Result templateContest(String templateContestId) {
        return TODO;
    }

    public static Result createAllTemplateContests() {
        Model.templateContests().remove();

        Iterable<TemplateMatchEvent> matchEventResults = Model.templateMatchEvents().find().sort("{startDate: 1}").as(TemplateMatchEvent.class);

        DateTime dateTime = null;
        List<TemplateMatchEvent> matchEvents = new ArrayList<>();   // Partidos que juntaremos en el mismo contests
        for (TemplateMatchEvent match: matchEventResults) {
            DateTime matchDateTime = new DateTime(match.startDate);
            if (dateTime == null) {
                dateTime = matchDateTime;
            }

            // El partido es de un dia distinto?
            if (dateTime.dayOfYear().get() != matchDateTime.dayOfYear().get()) {
                Logger.info("{} != {}", dateTime.dayOfYear().get(), matchDateTime.dayOfYear().get());

                // El dia anterior tenia un numero suficiente de partidos? (minimo 2)
                if (matchEvents.size() >= 2) {

                    // crear el contest
                    createTemplateContest(matchEvents);

                    // empezar a registrar los partidos del nuevo contest
                    matchEvents.clear();
                }
            }

            dateTime = matchDateTime;
            matchEvents.add(match);
        }

        // Tenemos partidos sin incluir en un contest?
        if (matchEvents.size() > 0) {
            createTemplateContest(matchEvents);
        }


        return redirect(routes.AdminController.templateContests());
    }

    public static void createTemplateContest(List<TemplateMatchEvent> templateMatchEvents) {
        if (templateMatchEvents.size() == 0) {
            Logger.error("createTemplateContest: templateMatchEvents is empty");
            return;
        }

        Date startDate = templateMatchEvents.get(0).startDate;

        TemplateContest templateContest = new TemplateContest();

        templateContest.name = String.format("Contest date %s", startDate);
        templateContest.postName = "Late evening";
        templateContest.minInstances = 3;
        templateContest.maxEntries = 10;
        templateContest.prizeType = PrizeType.STANDARD;
        templateContest.entryFee = 10000;
        templateContest.salaryCap = 100000;
        templateContest.startDate = startDate;
        templateContest.templateMatchEventIds = new ArrayList<>();

        for (TemplateMatchEvent match: templateMatchEvents) {
            templateContest.templateMatchEventIds.add(match.templateMatchEventId);
        }

        Logger.info("MockData: Template Contest: {} ({})", templateContest.templateMatchEventIds, startDate);

        Model.templateContests().insert(templateContest);
    }


    public static Result templateMatchEvents() {
        Iterable<TemplateMatchEvent> soccerMatchEventResults = Model.templateMatchEvents().find().as(TemplateMatchEvent.class);
        List<TemplateMatchEvent> matchEventList = ListUtils.asList(soccerMatchEventResults);

        return ok(views.html.template_match_event_list.render(matchEventList));
    }

    public static Result templateMatchEvent(String templateMatchEventId) {
        TemplateMatchEvent templateMatchEvent = Model.templateMatchEvents().findOne("{ _id : # }",
                new ObjectId(templateMatchEventId)).as(TemplateMatchEvent.class);
        return ok(views.html.template_match_event.render(templateMatchEvent));
    }

    public static Result createTemplateMatchEvent() {
        //TODO: En que fecha tendriamos que generar el partido?
        DateTime currentCreationDay =  new DateTime(2014, 10, 14, 12, 0, DateTimeZone.UTC);

        MockData.createTemplateMatchEvent(currentCreationDay);

        return redirect(routes.AdminController.templateMatchEvents());
    }

    public static Result templateSoccerTeams() {
        Iterable<TemplateSoccerTeam> soccerTeamResults = Model.templateSoccerTeams().find().as(TemplateSoccerTeam.class);
        List<TemplateSoccerTeam> soccerTeamList = ListUtils.asList(soccerTeamResults);

        return ok(views.html.template_soccer_team_list.render(soccerTeamList));
    }

    public static Result templateSoccerTeam(String templateSoccerTeamId) {
        TemplateSoccerTeam templateSoccerTeam = Model.templateSoccerTeams().findOne("{ _id : # }",
                new ObjectId(templateSoccerTeamId)).as(TemplateSoccerTeam.class);
        Iterable<TemplateSoccerPlayer> soccerPlayerResults = Model.templateSoccerPlayers().find("{ templateTeamId: # }",
                new ObjectId(templateSoccerTeamId)).as(TemplateSoccerPlayer.class);
        List<TemplateSoccerPlayer> soccerPlayerList = ListUtils.asList(soccerPlayerResults);
        return ok(views.html.template_soccer_team.render(templateSoccerTeam, soccerPlayerList));
    }

    public static Result templateSoccerPlayers() {
        Iterable<TemplateSoccerPlayer> soccerPlayerResults = Model.templateSoccerPlayers().find().as(TemplateSoccerPlayer.class);
        List<TemplateSoccerPlayer> soccerPlayerList = ListUtils.asList(soccerPlayerResults);

        return ok(views.html.template_soccer_player_list.render(soccerPlayerList));
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
        List<OptaPlayer> optaPlayerList = ListUtils.asList(optaPlayerResults);

        return ok(views.html.opta_soccer_player_list.render(optaPlayerList));
    }

    public static Result optaSoccerTeams() {
        Iterable<OptaTeam> optaTeamResults = Model.optaTeams().find().as(OptaTeam.class);
        List<OptaTeam> optaTeamList = ListUtils.asList(optaTeamResults);

        return ok(views.html.opta_soccer_team_list.render(optaTeamList));
    }

    public static Result optaMatchEvents() {
        Iterable<OptaMatchEvent> optaMatchEventResults = Model.optaMatchEvents().find().as(OptaMatchEvent.class);
        List<OptaMatchEvent> optaMatchEventList = ListUtils.asList(optaMatchEventResults);

        return ok(views.html.opta_match_event_list.render(optaMatchEventList));
    }

    public static Result simulator() {
        return ok(views.html.simulator.render());
    }

    public static Result startSimulator(){
        if (OptaSimulator.stoppedInstance()){
            OptaSimulator mySimulator = OptaSimulator.getInstance();
            mySimulator.launch(0L, System.currentTimeMillis(), 0, false, true, null);
            mySimulator.pause();
            FlashMessage.success("Simulator started");
            return ok(views.html.simulator.render());
            //return ok("Simulator started");
        }
        FlashMessage.warning("Simulator already started");
        return ok(views.html.simulator.render());
        //return ok("Simulator already started");

    }

    public static Result launchSimulator(){
        OptaSimulator mySimulator = OptaSimulator.getInstance();
        if (OptaSimulator.stoppedInstance()){
            mySimulator.launch(0L, System.currentTimeMillis(), 0, false, true, null);
            //Launch as a Thread, runs and parses all documents as fast as possible
            mySimulator.start();
            FlashMessage.success("Simulator started");
            return ok(views.html.simulator.render());
            //return ok("Simulator started");
        }
        FlashMessage.warning("Simulator already started");
        return ok(views.html.simulator.render());
        //return ok("Simulator already started");
    }

    public static Result pauseSimulator(){
        OptaSimulator.getInstance().pause();
        FlashMessage.success("Simulator paused");
        return ok(views.html.simulator.render());
        //return ok("Simulator paused");
    }

    public static Result resumeSimulator(){
        OptaSimulator mySimulator = OptaSimulator.getInstance();
        mySimulator.resumeLoop();
        FlashMessage.success("Simulator resumed");
        return ok(views.html.simulator.render());
        //return ok("Simulator resumed");
    }

    public static Result simulatorNext(){
        if (OptaSimulator.existsInstance()) {
            OptaSimulator mySimulator = OptaSimulator.getInstance();
            mySimulator.next();
            FlashMessage.success("Simulator resumed");
            return ok(views.html.simulator.render());
            //return ok("Simulator next step");
        } else {
            FlashMessage.danger("Simulator not running");
            return ok(views.html.simulator.render());
            //return ok("Simulator not running");
        }

    }

    public static Result stopSimulator(){
        OptaSimulator mySimulator = OptaSimulator.getInstance();
        mySimulator.halt();
        FlashMessage.success("Simulator stopped");
        return ok(views.html.simulator.render());
        //return ok("Simulator stopped");
    }

     public static Result optaEvents() {
         Iterable<OptaEvent> optaEventResults = Model.optaEvents().find().as(OptaEvent.class);
         List<OptaEvent> optaEventList = ListUtils.asList(optaEventResults);

         return ok(views.html.opta_event_list.render(optaEventList));
    }
}