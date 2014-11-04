package controllers;

import actions.AllowCors;
import play.Logger;
import play.data.Form;
import play.mvc.Controller;
import play.mvc.Result;

import static play.data.Form.form;

@AllowCors.Origin
public class LoggerController extends Controller {

    public static class Params {
        public String level;
        public String time;
        public String message;
    }

    public static Result log() {

        Form<Params> errorForm = form(Params.class).bindFromRequest();
        Params params = errorForm.get();

        String address = request().remoteAddress();

        if (params.level.equals("SHOUT")) {
            Logger.error("[WTF 101 from {} at {}] {}", address, params.time, params.message);
        }
        else {
            Logger.info("[{} from {} at {}] {}", params.level, address, params.time, params.message);
        }

        return ok();
    }

}
