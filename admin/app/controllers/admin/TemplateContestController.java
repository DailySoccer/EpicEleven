package controllers.admin;

import model.*;
import org.bson.types.ObjectId;
import org.joda.time.DateTime;
import play.Logger;
import play.data.Form;
import play.mvc.Controller;
import play.mvc.Result;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import static play.data.Form.form;

public class TemplateContestController extends Controller {
    public static Result index() {
        return ok(views.html.template_contest_list.render(TemplateContest.findAll()));
    }

    public static Result show(String templateContestId) {
        TemplateContest templateContest = TemplateContest.findOne(new ObjectId(templateContestId));
        return ok(views.html.template_contest.render(
                templateContest,
                templateContest.getTemplateMatchEvents(),
                TemplateSoccerTeam.findAllAsMap()));
    }

    public static Result newForm() {
        TemplateContestForm params = new TemplateContestForm();

        Form<TemplateContestForm> templateContestForm = Form.form(TemplateContestForm.class).fill(params);
        return ok(views.html.template_contest_add.render(templateContestForm, TemplateContestForm.matchEventsOptions(params.createdAt)));
    }

    public static Result edit(String templateContestId) {
        TemplateContest templateContest = TemplateContest.findOne(new ObjectId(templateContestId));
        TemplateContestForm params = new TemplateContestForm(templateContest);

        Form<TemplateContestForm> templateContestForm = Form.form(TemplateContestForm.class).fill(params);
        return ok(views.html.template_contest_add.render(templateContestForm, TemplateContestForm.matchEventsOptions(params.createdAt)));
    }

    public static Result destroy(String templateContestId) {
        TemplateContest templateContest = TemplateContest.findOne(new ObjectId(templateContestId));
        TemplateContest.remove(templateContest);
        return redirect(routes.TemplateContestController.index());
    }

    public static Result create() {
        Form<TemplateContestForm> templateContestForm = form(TemplateContestForm.class).bindFromRequest();
        if (templateContestForm.hasErrors()) {
            String createdAt = templateContestForm.field("createdAt").valueOr("0");
            return badRequest(views.html.template_contest_add.render(templateContestForm, TemplateContestForm.matchEventsOptions(Long.parseLong(createdAt))));
        }

        TemplateContestForm params = templateContestForm.get();

        boolean isNew = params.id.isEmpty();

        TemplateContest templateContest = new TemplateContest();

        templateContest.templateContestId = !isNew ? new ObjectId(params.id) : null;
        templateContest.state = params.state;
        templateContest.name = params.name;
        templateContest.minInstances = params.minInstances;
        templateContest.maxEntries = params.maxEntries;
        templateContest.salaryCap = params.salaryCap;
        templateContest.entryFee = params.entryFee;
        templateContest.prizeType = params.prizeType;

        templateContest.activationAt = params.activationAt;
        templateContest.createdAt = new Date(params.createdAt);

        Date startDate = null;
        templateContest.templateMatchEventIds = new ArrayList<>();
        for (String templateMatchEventId: params.templateMatchEvents) {
            TemplateMatchEvent templateMatchEvent = TemplateMatchEvent.findOne(new ObjectId(templateMatchEventId));
            templateContest.templateMatchEventIds.add(templateMatchEvent.templateMatchEventId);

            if (startDate == null || templateMatchEvent.startDate.before(startDate)) {
                startDate = templateMatchEvent.startDate;
            }
        }
        templateContest.startDate = startDate;

        if (isNew) {
            Model.templateContests().insert(templateContest);
        }
        else {
            Model.templateContests().update("{_id: #}", templateContest.templateContestId).with(templateContest);
        }

        return redirect(routes.TemplateContestController.index());
    }

    public static Result createAll() {
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
                // Logger.info("{} != {}", dateTime.dayOfYear().get(), matchDateTime.dayOfYear().get());

                // El dia anterior tenia un numero suficiente de partidos? (minimo 2)
                if (matchEvents.size() >= 2) {

                    // crear el contest
                    createMock(matchEvents);

                    // empezar a registrar los partidos del nuevo contest
                    matchEvents.clear();
                }
            }

            dateTime = matchDateTime;
            matchEvents.add(match);
        }

        // Tenemos partidos sin incluir en un contest?
        if (matchEvents.size() > 0) {
            createMock(matchEvents);
        }

        return redirect(routes.TemplateContestController.index());
    }

    public static void createMock(List<TemplateMatchEvent> templateMatchEvents) {
        createMock(templateMatchEvents, 0, 3, PrizeType.FREE);
        createMock(templateMatchEvents, 0, 5, PrizeType.FREE);
        createMock(templateMatchEvents, 0, 10, PrizeType.FREE);
        createMock(templateMatchEvents, 0, 25, PrizeType.FREE);


        for (int i = 1; i<=6; i++) {
            createMock(templateMatchEvents, i, 2, PrizeType.WINNER_TAKES_ALL);
            createMock(templateMatchEvents, i, 3, PrizeType.WINNER_TAKES_ALL);
            createMock(templateMatchEvents, i, 5, PrizeType.WINNER_TAKES_ALL);
            createMock(templateMatchEvents, i, 10, PrizeType.WINNER_TAKES_ALL);
            createMock(templateMatchEvents, i, 25, PrizeType.WINNER_TAKES_ALL);

            createMock(templateMatchEvents, i, 3, PrizeType.TOP_3_GET_PRIZES);
            createMock(templateMatchEvents, i, 5, PrizeType.TOP_3_GET_PRIZES);
            createMock(templateMatchEvents, i, 10, PrizeType.TOP_3_GET_PRIZES);
            createMock(templateMatchEvents, i, 25, PrizeType.TOP_3_GET_PRIZES);

            createMock(templateMatchEvents, i, 3, PrizeType.TOP_THIRD_GET_PRIZES);
            createMock(templateMatchEvents, i, 5, PrizeType.TOP_THIRD_GET_PRIZES);
            createMock(templateMatchEvents, i, 10, PrizeType.TOP_THIRD_GET_PRIZES);
            createMock(templateMatchEvents, i, 25, PrizeType.TOP_THIRD_GET_PRIZES);

            createMock(templateMatchEvents, i, 3, PrizeType.FIFTY_FIFTY);
            createMock(templateMatchEvents, i, 5, PrizeType.FIFTY_FIFTY);
            createMock(templateMatchEvents, i, 10, PrizeType.FIFTY_FIFTY);
            createMock(templateMatchEvents, i, 25, PrizeType.FIFTY_FIFTY);
        }
    }

    public static void createMock(List<TemplateMatchEvent> templateMatchEvents, int entryFee, int maxEntries, PrizeType prizeType) {
        if (templateMatchEvents.size() == 0) {
            Logger.error("create: templateMatchEvents is empty");
            return;
        }

        Date startDate = templateMatchEvents.get(0).startDate;

        TemplateContest templateContest = new TemplateContest();

        templateContest.name = "%StartDate";
        templateContest.minInstances = 3;
        templateContest.maxEntries = maxEntries;
        templateContest.prizeType = prizeType;
        templateContest.entryFee = entryFee;
        templateContest.salaryCap = 90000;
        templateContest.startDate = startDate;
        templateContest.templateMatchEventIds = new ArrayList<>();

        // Se activará 2 dias antes a la fecha del partido
        templateContest.activationAt = new DateTime(startDate).minusDays(2).toDate();

        templateContest.createdAt = GlobalDate.getCurrentDate();

        for (TemplateMatchEvent match: templateMatchEvents) {
            templateContest.templateMatchEventIds.add(match.templateMatchEventId);
        }

        // Logger.info("MockData: Template Contest: {} ({})", templateContest.templateMatchEventIds, GlobalDate.formatDate(startDate));

        Model.templateContests().insert(templateContest);
    }
}
