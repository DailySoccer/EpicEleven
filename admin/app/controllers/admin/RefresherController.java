package controllers.admin;

import model.GlobalDate;
import model.opta.OptaXmlUtils;
import play.Logger;
import play.libs.F;
import play.libs.ws.WS;
import play.libs.ws.WSResponse;
import play.mvc.Controller;
import play.mvc.Result;
import utils.StringUtils;

import java.util.Date;

public class RefresherController extends Controller {

    public static Result index() {
        return ok(views.html.refresher.render());
    }

    public static Result inProgress() {
        return ok(String.valueOf(true));
    }

    public static Result importFromLast() {

        return ok("Finished importing");
    }

    public static Result lastDate() {
        return ok(String.valueOf(OptaXmlUtils.getLastDate().getTime()));    // Returns date in millis
    }
}
