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
        public String email;
        public String message;
        public String userAgent;
    }

    public static Result log() {

        Form<Params> errorForm = form(Params.class).bindFromRequest();
        Params params = errorForm.get();

        String address = request().remoteAddress();

        if (params.level.equals("SHOUT")) {
            Logger.error("[WTF 101 from {}, {}, {}] {}", params.email, address, params.userAgent, params.message);
        }
        else {
            Logger.info("[{} from {}, {}, {}] {}", params.level, params.email, address, params.userAgent, params.message);
        }

        return ok();
    }

}
