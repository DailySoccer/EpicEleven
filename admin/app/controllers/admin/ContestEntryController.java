package controllers.admin;

import model.*;
import org.bson.types.ObjectId;
import play.data.Form;
import play.mvc.Controller;
import play.mvc.Result;
import utils.ListUtils;

import java.util.List;

import static play.data.Form.form;

public class ContestEntryController extends Controller {
    public static Result index() {
        Iterable<ContestEntry> contestEntryResults = Model.contestEntries().find().as(ContestEntry.class);
        List<ContestEntry> contestEntryList = ListUtils.asList(contestEntryResults);

        return ok(views.html.contest_entry_list.render(contestEntryList));
    }

    public static Result newForm() {
        Form<ContestEntryForm> contestEntryForm = Form.form(ContestEntryForm.class);
        return ok(views.html.contest_entry_add.render(contestEntryForm, null));
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

    public static Result create() {
        /*
            Desconectada por usar "ContestEntry.createFromOptaIds"

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
        */

        return redirect(routes.ContestEntryController.index());
    }

    private static Result enterContestWithForm(Form<ContestEntryForm> contestEntryForm) {
        ContestEntryForm params = contestEntryForm.get();
        String contestId = params.contestId;

        return ok(views.html.contest_entry_add.render(contestEntryForm, getTemplateMatchEvents(contestId)));
    }

    private static List<TemplateMatchEvent> getTemplateMatchEvents(String contestId) {
        // Pasar la lista de partidos
        Contest contest = Model.contests().findOne("{_id: #}", new ObjectId(contestId)).as(Contest.class);
        TemplateContest templateContest = Model.templateContests().findOne("{_id: #}", contest.templateContestId).as(TemplateContest.class);

        Iterable<TemplateMatchEvent> templateMatchEventsResults = TemplateMatchEvent.findAll(templateContest.templateMatchEventIds);
        return ListUtils.asList(templateMatchEventsResults);
    }
}
