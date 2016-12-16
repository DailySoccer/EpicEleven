package controllers.admin;

import com.google.common.collect.ImmutableMap;
import model.JsonViews;
import model.Model;
import play.mvc.Controller;
import play.mvc.Result;

public class MaintenanceController extends Controller {

    public static Result index() {
        long numVirtualTemplateContests = Model.templateContests().count("");
        long numVirtualContests = 0;
        long numVirtualTemplateMatchEvents = 0;

        return ok(views.html.maintenance.render(ImmutableMap.of(
                "numVirtualTemplateContests", numVirtualTemplateContests,
                "numVirtualContests", numVirtualContests,
                "numVirtualTemplateMatchEvents", numVirtualTemplateMatchEvents
        )));
    }
}