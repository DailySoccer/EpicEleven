package controllers.admin;

import actions.CheckTargetEnvironment;
import model.Model;
import model.opta.OptaXmlUtils;
import play.libs.F;
import play.mvc.Controller;
import play.mvc.Result;
import play.twirl.api.Html;
import views.html.refresher;

@CheckTargetEnvironment
public class RefresherController extends Controller {

    public static F.Promise<Result> index() {
        F.Promise<Html> htmlPromise = F.Promise.promise(() ->
                refresher.render(getIsTicking()));

        return htmlPromise.map((Html i) -> ok(i));
    }

    public static Result inProgress() {
        return ok(String.valueOf(getIsTicking()));
    }

    public static Result startStopRefresh() {
        Model.actors().tell("RefresherActor", "StartStopTicking");
        return redirect(routes.RefresherController.index());
    }

    public static F.Promise<Result> lastDate() {
        F.Promise<String> stringPromise = F.Promise.promise(() ->
                String.valueOf(OptaXmlUtils.getLastDate().getTime()));

        return stringPromise.map((String i) -> ok(i)); // Returns date in millis
    }

    static private boolean getIsTicking() {
        return Model.actors().tellAndAwait("RefresherActor", "GetIsTicking");
    }
}
