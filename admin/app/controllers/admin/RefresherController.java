package controllers.admin;

import model.Model;
import model.opta.OptaXmlUtils;
import play.Logger;
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
        boolean isTicking = Model.actors().tellAndAwait("RefresherActor", "StartStopTicking");
        Logger.debug("Retornando en el otro {}", isTicking);
        return redirect(routes.RefresherController.index());
    }

    public static Result lastDate() {
        return ok(String.valueOf(OptaXmlUtils.getLastDate().getTime()));    // Returns date in millis
    }

    static private boolean getIsTicking() {
        boolean isTicking = Model.actors().tellAndAwait("RefresherActor", "GetIsTicking");
        Logger.debug("Retornando {}", isTicking);
        return isTicking;
    }
}
