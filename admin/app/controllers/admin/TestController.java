package controllers.admin;

import play.mvc.Controller;
import play.mvc.Result;

import java.util.Date;

public class TestController extends Controller {
    static public Result start() {
        if (!OptaSimulator.isCreated())
            OptaSimulator.init();

        OptaSimulator.instance().reset(false);
        return ok("OK");
    }

    static public Result gotoDate(Long timestamp) {
        Date date = new Date(timestamp);

        if (!OptaSimulator.isCreated())
            OptaSimulator.init();

        OptaSimulator.instance().gotoDate(date);
        return ok("OK");
    }

    static public Result importEverything() {
        ImportController.importSalaries();
        ImportController.importAllTeams();
        ImportController.importAllSoccers();
        ImportController.importAllMatchEvents();
        return ok("OK");
    }

    static public Result initialSetup() {
        importEverything();
        PointsTranslationController.resetToDefault();
        TemplateContestController.createAll();
        return ok("OK");
    }
}
