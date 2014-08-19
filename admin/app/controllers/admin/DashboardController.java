package controllers.admin;

import model.MockData;
import model.Model;
import play.mvc.Controller;
import play.mvc.Result;

public class DashboardController extends Controller {
    public static Result index() {
        return ok(views.html.dashboard.render());
    }

    static public Result importEverything() {
        ImportController.importSalaries();
        ImportController.importAllTeams();
        ImportController.importAllSoccers();
        ImportController.importAllMatchEvents();
        return ok(views.html.dashboard.render());
    }

    static public Result initialSetup() {
        importEverything();
        PointsTranslationController.resetToDefault();
        TemplateContestController.createAll();
        return ok(views.html.dashboard.render());
    }

    public static Result resetDB() {
        Model.resetDB();
        MockData.ensureMockDataUsers();

        FlashMessage.success("Reset DB: OK");
        return ok(views.html.dashboard.render());
    }
}
