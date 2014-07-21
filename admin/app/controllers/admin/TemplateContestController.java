package controllers.admin;

import model.*;
import org.bson.types.ObjectId;
import org.joda.time.DateTime;
import play.Logger;
import play.data.Form;
import play.mvc.Controller;
import play.mvc.Result;
import utils.ListUtils;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

import static play.data.Form.form;

public class TemplateContestController extends Controller {
    public static Result index() {
        return ok(views.html.template_contest_list.render(TemplateContest.findAll()));
    }

    public static Result show(String templateContestId) {
        return TODO;
    }

    public static Result newForm() {
        TemplateContestForm params = new TemplateContestForm();

        Form<TemplateContestForm> templateContestForm = Form.form(TemplateContestForm.class).fill(params);
        return ok(views.html.template_contest_add.render(templateContestForm, TemplateContestForm.matchEventsOptions()));
    }

    public static Result edit(String templateContestId) {
        TemplateContest templateContest = TemplateContest.findOne(new ObjectId(templateContestId));
        TemplateContestForm params = new TemplateContestForm(templateContest);

        Form<TemplateContestForm> templateContestForm = Form.form(TemplateContestForm.class).fill(params);
        return ok(views.html.template_contest_add.render(templateContestForm, TemplateContestForm.matchEventsOptions()));
    }

    public static Result destroy(String templateContestId) {
        TemplateContest templateContest = TemplateContest.findOne(new ObjectId(templateContestId));
        TemplateContest.remove(templateContest);
        return redirect(routes.TemplateContestController.index());
    }

    public static Result create() {
        Form<TemplateContestForm> templateContestForm = form(TemplateContestForm.class).bindFromRequest();
        if (templateContestForm.hasErrors()) {
            return badRequest(views.html.template_contest_add.render(templateContestForm, TemplateContestForm.matchEventsOptions()));
        }

        TemplateContestForm params = templateContestForm.get();

        boolean isNew = params.id.isEmpty();

        TemplateContest templateContest = new TemplateContest();

        templateContest.templateContestId = isNew ? new ObjectId() : new ObjectId(params.id);
        templateContest.state = params.state;
        templateContest.name = params.name;
        templateContest.postName = params.postName;
        templateContest.minInstances = params.minInstances;
        templateContest.maxEntries = params.maxEntries;
        templateContest.salaryCap = params.salaryCap;
        templateContest.entryFee = params.entryFee;
        templateContest.prizeType = params.prizeType;

        templateContest.activationAt = params.activationAt;
        templateContest.createdAt = params.createdAt;

        /*
        // Si está activo y la fecha de activación se ha puesto en un futuro
        if (templateContest.isActive() && templateContest.activationAt.after(GlobalDate.getCurrentDate())) {
            // Lo apagamos... hasta que suceda esa fecha
            templateContest.state = TemplateContest.State.OFF;
        }
        */

        Date startDate = null;
        templateContest.templateMatchEventIds = new ArrayList<>();
        for (String optaMatchEventId: params.templateMatchEvents) {
            TemplateMatchEvent templateMatchEvent = TemplateMatchEvent.findOneFromOptaId(optaMatchEventId);
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
                Logger.info("{} != {}", dateTime.dayOfYear().get(), matchDateTime.dayOfYear().get());

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
        if (templateMatchEvents.size() == 0) {
            Logger.error("create: templateMatchEvents is empty");
            return;
        }

        Date startDate = templateMatchEvents.get(0).startDate;

        TemplateContest templateContest = new TemplateContest();

        templateContest.name = String.format("%s", startDate);
        templateContest.postName = "Late evening";
        templateContest.minInstances = 3;
        templateContest.maxEntries = 10;
        templateContest.prizeType = PrizeType.STANDARD;
        templateContest.entryFee = 10000;
        templateContest.salaryCap = 100000;
        templateContest.startDate = startDate;
        templateContest.templateMatchEventIds = new ArrayList<>();

        // Se activará 2 dias antes a la fecha del partido
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(startDate);
        calendar.add(Calendar.DAY_OF_MONTH, -2);
        templateContest.activationAt = calendar.getTime();

        templateContest.createdAt = GlobalDate.getCurrentDate();

        for (TemplateMatchEvent match: templateMatchEvents) {
            templateContest.templateMatchEventIds.add(match.templateMatchEventId);
        }

        Logger.info("MockData: Template Contest: {} ({})", templateContest.templateMatchEventIds, startDate);

        Model.templateContests().insert(templateContest);
    }
}
