package controllers.admin;

import model.*;
import model.opta.OptaMatchEvent;
import model.opta.OptaPlayer;
import model.opta.OptaTeam;
import model.opta.OptaEvent;
import org.bson.types.ObjectId;

import java.util.*;

import org.joda.time.DateTime;
import play.Logger;
import play.data.Form;
import play.data.format.Formats;
import play.mvc.Controller;
import play.mvc.Result;
import utils.ListUtils;

import static play.data.Form.form;
import static utils.OptaUtils.recalculateAllEvents;

import play.data.validation.Constraints.Required;
import views.html.points_translation_add;


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

    /**
     * IMPORT TEAMS from OPTA
     *
     */
    public static void evaluateDirtyTeams(List<OptaTeam> news, List<OptaTeam> changes, List<OptaTeam> invalidates) {
        Iterable<OptaTeam> teamsDirty = Model.optaTeams().find("{dirty: true}").as(OptaTeam.class);
        for(OptaTeam optaTeam : teamsDirty) {
            TemplateSoccerTeam template = Model.templateSoccerTeams().findOne("{optaTeamId: #}", optaTeam.id).as(TemplateSoccerTeam.class);
            if (template == null) {
                if (TemplateSoccerTeam.isInvalid(optaTeam)) {
                    if (invalidates != null)
                        invalidates.add(optaTeam);
                }
                else if (news != null) {
                    news.add(optaTeam);
                }
            }
            else if (changes != null && !template.isEqual(optaTeam)) {
                changes.add(optaTeam);
            }
        }
    }

    public static int importTeams(List<OptaTeam> teams) {
        for(OptaTeam optaTeam : teams) {
            TemplateSoccerTeam.importTeam(optaTeam);
        }
        return teams.size();
    }

    public static Result showImportTeams() {
        List<OptaTeam> teamsNew = new ArrayList<>();
        List<OptaTeam> teamsChanged = new ArrayList<>();
        List<OptaTeam> teamsInvalidated = new ArrayList<>();
        evaluateDirtyTeams(teamsNew, teamsChanged, teamsInvalidated);
        return ok(views.html.import_teams.render(teamsNew, teamsChanged, teamsInvalidated));
    }

    public static Result importAllTeams() {
        List<OptaTeam> teamsNew = new ArrayList<>();
        List<OptaTeam> teamsChanged = new ArrayList<>();
        evaluateDirtyTeams(teamsNew, teamsChanged, null);

        int news = importTeams(teamsNew);
        FlashMessage.success(String.format("Imported %d teams New", news));

        int changes = importTeams(teamsChanged);
        FlashMessage.success( String.format("Imported %d teams Changed", changes) );
        return redirect(routes.AdminController.showImportTeams());
    }

    public static Result importAllNewTeams() {
        List<OptaTeam> teamsNew = new ArrayList<>();
        evaluateDirtyTeams(teamsNew, null, null);

        int news = importTeams(teamsNew);
        FlashMessage.success( String.format("Imported %d teams New", news) );
        return redirect(routes.AdminController.showImportTeams());
    }

    public static Result importAllChangedTeams() {
        List<OptaTeam> teamsChanged = new ArrayList<>();
        evaluateDirtyTeams(null, teamsChanged, null);

        int changes = importTeams(teamsChanged);
        FlashMessage.success( String.format("Imported %d teams Changed", changes) );
        return redirect(routes.AdminController.showImportTeams());
    }

    /**
     * IMPORT SOCCERS from OPTA
     *
     */
    public static void evaluateDirtySoccers(List<OptaPlayer> news, List<OptaPlayer> changes, List<OptaPlayer> invalidates) {
        Iterable<OptaPlayer> soccersDirty = Model.optaPlayers().find("{dirty: true}").as(OptaPlayer.class);
        for(OptaPlayer optaSoccer : soccersDirty) {
            TemplateSoccerPlayer template = Model.templateSoccerPlayers().findOne("{optaPlayerId: #}", optaSoccer.id).as(TemplateSoccerPlayer.class);
            if (template == null) {
                if (TemplateSoccerPlayer.isInvalid(optaSoccer)) {
                    if (invalidates != null)
                        invalidates.add(optaSoccer);
                }
                else if (news != null) {
                    news.add(optaSoccer);
                }
            }
            else if (changes != null && !template.isEqual(optaSoccer)) {
                changes.add(optaSoccer);
            }
        }
    }

    public static int importSoccers(List<OptaPlayer> soccers) {
        int numImported = 0;
        for(OptaPlayer optaSoccer : soccers) {
            if (TemplateSoccerPlayer.importSoccer(optaSoccer)) {
                numImported++;
            }
        }
        return numImported;
    }

    public static Result showImportSoccers() {
        List<OptaPlayer> playersNew = new ArrayList<>();
        List<OptaPlayer> playersChanged = new ArrayList<>();
        List<OptaPlayer> playersInvalidated = new ArrayList<>();
        evaluateDirtySoccers(playersNew, playersChanged, playersInvalidated);
        return ok(views.html.import_soccers.render(playersNew, playersChanged, playersInvalidated));
    }

    public static Result importAllSoccers() {
        List<OptaPlayer> playersNew = new ArrayList<>();
        List<OptaPlayer> playersChanged = new ArrayList<>();
        evaluateDirtySoccers(playersNew, playersChanged, null);

        int news = importSoccers(playersNew);
        FlashMessage.success( String.format("Imported %d soccers New", news) );

        int changes = importSoccers(playersChanged);
        FlashMessage.success( String.format("Imported %d soccers Changed", changes) );
        return redirect(routes.AdminController.showImportSoccers());
    }

    public static Result importAllNewSoccers() {
        List<OptaPlayer> playersNew = new ArrayList<>();
        evaluateDirtySoccers(playersNew, null, null);

        int news = importSoccers(playersNew);
        FlashMessage.success( String.format("Imported %d soccers New", news) );
        return redirect(routes.AdminController.showImportSoccers());
    }

    public static Result importAllChangedSoccers() {
        List<OptaPlayer> playersChanged = new ArrayList<>();
        evaluateDirtySoccers(null, playersChanged, null);

        int changes = importSoccers(playersChanged);
        FlashMessage.success( String.format("Imported %d soccers Changed", changes) );
        return redirect(routes.AdminController.showImportSoccers());
    }


    /**
     * IMPORT MATCH EVENTS from OPTA
     *
     */
    public static void evaluateDirtyMatchEvents(List<OptaMatchEvent> news, List<OptaMatchEvent> changes, List<OptaMatchEvent> invalidates) {
        Iterable<OptaMatchEvent> matchesDirty = Model.optaMatchEvents().find("{dirty: true}").as(OptaMatchEvent.class);
        for(OptaMatchEvent optaMatch : matchesDirty) {
            TemplateMatchEvent template = Model.templateMatchEvents().findOne("{optaMatchEventId: #}", optaMatch.id).as(TemplateMatchEvent.class);
            if (template == null) {
                if (TemplateMatchEvent.isInvalid(optaMatch)) {
                    if (invalidates != null)
                        invalidates.add(optaMatch);
                }
                else if (news != null) {
                    news.add(optaMatch);
                }
            }
            else if (changes != null && !template.isEqual(optaMatch)) {
                changes.add(optaMatch);
            }
        }
    }

    public static int importMatchEvents(List<OptaMatchEvent> matches) {
        int numImported = 0;
        for(OptaMatchEvent optaMatchEvent : matches) {
            if (TemplateMatchEvent.importMatchEvent(optaMatchEvent)) {
                numImported++;
            }
        }
        return numImported;
    }

    public static Result showImportMatchEvents() {
        List<OptaMatchEvent> matchesNew = new ArrayList<>();
        List<OptaMatchEvent> matchesChanged = new ArrayList<>();
        List<OptaMatchEvent> matchesInvalidated = new ArrayList<>();
        evaluateDirtyMatchEvents(matchesNew, matchesChanged, matchesInvalidated);
        return ok(views.html.import_match_events.render(matchesNew, matchesChanged, matchesInvalidated));
    }

    public static Result importAllMatchEvents() {
        List<OptaMatchEvent> matchesNew = new ArrayList<>();
        List<OptaMatchEvent> matchesChanged = new ArrayList<>();
        evaluateDirtyMatchEvents(matchesNew, matchesChanged, null);

        int news = importMatchEvents(matchesNew);
        FlashMessage.success( String.format("Imported %d match events New", news) );

        int changes = importMatchEvents(matchesChanged);
        FlashMessage.success( String.format("Imported %d match events Changed", changes) );
        return redirect(routes.AdminController.showImportMatchEvents());
    }

    public static Result importAllNewMatchEvents() {
        List<OptaMatchEvent> matchesNew = new ArrayList<>();
        evaluateDirtyMatchEvents(matchesNew, null, null);

        int news = importMatchEvents(matchesNew);
        FlashMessage.success( String.format("Imported %d match events New", news) );
        return redirect(routes.AdminController.showImportMatchEvents());
    }

    public static Result importAllChangedMatchEvents() {
        List<OptaMatchEvent> matchesChanged = new ArrayList<>();
        evaluateDirtyMatchEvents(null, matchesChanged, null);

        int changes = importMatchEvents(matchesChanged);
        FlashMessage.success( String.format("Imported %d match events Changed", changes) );
        return redirect(routes.AdminController.showImportMatchEvents());
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

        TemplateSoccerPlayer templateSoccerPlayer = TemplateSoccerPlayer.find(new ObjectId(playerId));
        Contest contest = Contest.find(new ObjectId(contestId));
        TemplateContest templateContest = TemplateContest.find(contest.templateContestId);
        List<TemplateMatchEvent> templateMatchEvents = templateContest.templateMatchEvents();

        for (TemplateMatchEvent templateMatchEvent : templateMatchEvents) {
            optaEventList.addAll(OptaEvent.filter(templateMatchEvent.optaMatchEventId, templateSoccerPlayer.optaPlayerId));
        }

        return ok(views.html.player_fantasy_points.render(templateSoccerPlayer, optaEventList));
    }

    public static Result showPlayerFantasyPointsInMatchEvent(String templateMatchEventId, String playerId) {
        List<OptaEvent> optaEventList = new ArrayList<>();

        TemplateSoccerPlayer templateSoccerPlayer = TemplateSoccerPlayer.find(new ObjectId(playerId));
        TemplateMatchEvent templateMatchEvent = TemplateMatchEvent.find(new ObjectId(templateMatchEventId));

        optaEventList.addAll(OptaEvent.filter(templateMatchEvent.optaMatchEventId, templateSoccerPlayer.optaPlayerId));

        return ok(views.html.player_fantasy_points.render(templateSoccerPlayer, optaEventList));
    }

    public static Result liveContestEntry(String contestEntryId) {
        ContestEntry contestEntry = ContestEntry.find(new ObjectId(contestEntryId));
        List<SoccerPlayer> soccer_players = ContestEntry.getSoccerPlayers(contestEntryId);
        return ok(views.html.live_contest_entry.render(contestEntry, soccer_players));
    }

    public static Result liveContestEntries() {
        Iterable<ContestEntry> contestEntryResults = Model.contestEntries().find().as(ContestEntry.class);
        List<ContestEntry> contestEntryList = ListUtils.asList(contestEntryResults);

        return ok(views.html.live_contest_entry_list.render(contestEntryList));
    }

    public static Result liveMatchEvent(String liveMatchEventId) {
        ObjectId id = new ObjectId(liveMatchEventId);

        // Obtener la version actualizada
        LiveMatchEvent liveMatchEvent = LiveMatchEvent.find(id);
        return ok(views.html.live_match_event.render(liveMatchEvent));
    }

    public static Result liveMatchEventWithTemplate(String templateMatchEventId) {
        TemplateMatchEvent templateMatchEvent = TemplateMatchEvent.find(new ObjectId(templateMatchEventId));
        LiveMatchEvent liveMatchEvent = LiveMatchEvent.find(templateMatchEvent);
        return liveMatchEvent(liveMatchEvent.liveMatchEventId.toString());
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

        boolean success = params.id.isEmpty()? PointsTranslation.createPointForEvent(params.eventType.code, params.points):
                                               PointsTranslation.editPointForEvent(new ObjectId(params.id), params.points);

        if (!success) {
            FlashMessage.warning("Points Translation invalid");
            return badRequest(views.html.points_translation_add.render(pointsTranslationForm));
        }

        Logger.info("Event Type ({}) = {} points", params.eventType, params.points);

        return redirect(routes.AdminController.pointsTranslations());
    }

    public static Result editPointForEvent(String pointsTranslationId) {
        PointsTranslation pointsTranslation = Model.pointsTranslation().findOne("{_id: #}",
                new ObjectId(pointsTranslationId)).as(PointsTranslation.class);
        Form<PointsTranslationForm> pointsTranslationForm = Form.form(PointsTranslationForm.class).
                                                            fill(new PointsTranslationForm(pointsTranslation));
        return ok(points_translation_add.render(pointsTranslationForm));
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

    public static Result pointsTranslationsHistory(int eventType) {
        Iterable<PointsTranslation> pointsTranslationList = Model.pointsTranslation().find("{eventTypeId: #}", eventType).
                                                            sort("{timestamp: -1}").as(PointsTranslation.class);

        List<PointsTranslation> pointsTranslationResult = ListUtils.asList(pointsTranslationList);
        return ok(views.html.points_translation_list.render(pointsTranslationResult));
    }

    public static Result pointsTranslations() {
        List<Integer> differentTypes = Model.pointsTranslation().distinct("eventTypeId").as(Integer.class);
        List<PointsTranslation> pointsTranslationList = new ArrayList<PointsTranslation>();
        for (Integer differentType: differentTypes){
            pointsTranslationList.add((PointsTranslation)Model.pointsTranslation().
                                       find("{eventTypeId: #}", differentType).sort("{timestamp: -1}").limit(1).
                                       as(PointsTranslation.class).iterator().next());
        }

        return ok(views.html.points_translation_list.render(pointsTranslationList));
    }

    public static Result addContestEntry() {
        Form<ContestEntryForm> contestEntryForm = Form.form(ContestEntryForm.class);
        return ok(views.html.contest_entry_add.render(contestEntryForm, null));
    }

    private static List<TemplateMatchEvent> getTemplateMatchEvents(String contestId) {
        // Pasar la lista de partidos
        Contest contest = Model.contests().findOne("{_id: #}", new ObjectId(contestId)).as(Contest.class);
        TemplateContest templateContest = Model.templateContests().findOne("{_id: #}", contest.templateContestId).as(TemplateContest.class);

        Iterable<TemplateMatchEvent> templateMatchEventsResults = TemplateMatchEvent.find("_id", templateContest.templateMatchEventIds);
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

        boolean success = ContestEntry.createFromOptaIds(params.userId, params.contestId, params.getTeam());
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

        Iterable<TemplateMatchEvent> templateMatchEventsResults = Model.find("_id", templateContest.templateMatchEventIds);
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
        Model.instantiateContests();
        return redirect(routes.AdminController.contests());
    }

    public static Result deleteContest(String contestId) {
        Contest contest = Contest.find(new ObjectId(contestId));
        Contest.remove(contest);
        return redirect(routes.AdminController.contests());
    }

    public static Result deleteTemplateContest(String templateContestId) {
        TemplateContest templateContest = TemplateContest.find(new ObjectId(templateContestId));
        TemplateContest.remove(templateContest);
        return redirect(routes.AdminController.templateContests());
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

        templateContest.templateContestId = params.id.isEmpty() ? new ObjectId() : new ObjectId(params.id);
        templateContest.state = params.state;
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

        /*
        for(String p: params.templateMatchEvents) {
            Logger.info("{}", p);
        }
        */

        if (params.id.isEmpty()) {
            Model.templateContests().insert(templateContest);
        }
        else {
            Model.templateContests().update("{_id: #}", templateContest.templateContestId).with(templateContest);
        }

        if (templateContest.isActive()) {
            templateContest.instantiate();
        }

        return redirect(routes.AdminController.templateContests());
    }

    public static Result addTemplateContest() {
        Form<TemplateContestForm> templateContestForm = Form.form(TemplateContestForm.class);
        return ok(views.html.template_contest_add.render(templateContestForm, TemplateContestForm.matchEventsOptions()));
    }

    public static Result editTemplateContest(String templateContestId) {
        TemplateContest templateContest = TemplateContest.find(new ObjectId(templateContestId));
        TemplateContestForm params = new TemplateContestForm(templateContest);

        Form<TemplateContestForm> templateContestForm = Form.form(TemplateContestForm.class).fill(params);
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
        Iterable<TemplateContest> templateContestResults = TemplateContest.find("_id", idsList);

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

    public static Result updateOptaEvents() {
        recalculateAllEvents();
        FlashMessage.success("Events recalculated");
        return redirect(routes.AdminController.pointsTranslations());
    }
    public static Result simulator() {
        return ok(views.html.simulator.render());
    }

    public static Result startSimulator(){
        if (OptaSimulator.stoppedInstance()){
            OptaSimulator mySimulator = OptaSimulator.getInstance();
            mySimulator.launch(0L, System.currentTimeMillis(), 0, false, true, null);
            mySimulator.start();
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

    public static class GotoSimParams {
        @Required @Formats.DateTime (pattern = "yyyy-MM-dd'T'HH:mm")
        public Date date;
    }

    public static Result gotoSimulator(){
        Form<GotoSimParams> gotoForm = form(GotoSimParams.class).bindFromRequest();
        if (!gotoForm.hasErrors()){
            GotoSimParams params = gotoForm.get();
            OptaSimulator mySimulator = OptaSimulator.getInstance();
            mySimulator.addPause(params.date);
            FlashMessage.success("Simulator going");
            return ok(views.html.simulator.render());
        } else {
            FlashMessage.danger("Wrong button pressed");
            return ok(views.html.simulator.render());
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