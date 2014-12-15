package controllers.admin;

import model.MockData;
import model.Model;
import model.opta.OptaCompetition;
import play.mvc.Controller;
import play.mvc.Result;

public class DashboardController extends Controller {
    public static Result index() {
        return ok(views.html.dashboard.render(OptaCompetition.findAllActive()));
    }

    static public Result initialSetup() {
        PointsTranslationController.resetToDefault();
        TemplateContestController.createAll();
        return index();
    }

    public static Result resetDB() {
        Model.resetDB();
        MockData.ensureMockDataUsers();
        MockData.ensureCompetitions();

        FlashMessage.success("Reset DB: OK");
        return index();
    }
}