package controllers.admin;

import model.Model;
import model.opta.OptaXmlUtils;
import play.mvc.Controller;
import play.mvc.Result;

public class RefresherController extends Controller {

    public static Result index() {
        return ok(views.html.refresher.render(getIsTicking()));
    }

    public static Result inProgress() {
        return ok(String.valueOf(getIsTicking()));
    }

    public static Result startStopRefresh() {
        Model.actors().tell("RefresherActor", "StartStopTicking");
        return redirect(routes.RefresherController.index());
    }

    public static Result lastDate() {
        return ok(String.valueOf(OptaXmlUtils.getLastDate().getTime()));    // Returns date in millis
    }

    static private boolean getIsTicking() {
        return Model.actors().tellAndAwait("RefresherActor", "GetIsTicking");
    }
}
