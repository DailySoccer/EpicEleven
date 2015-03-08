package controllers;

import actions.AllowCors;
import play.Logger;
import play.data.Form;
import play.libs.F;
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

    public static F.Promise<Result> log() {
        return F.Promise.promise(() ->_log()).map((Boolean i) -> ok());
    }

    private static boolean _log() {
        Form<Params> errorForm = form(Params.class).bindFromRequest();
        Params params = errorForm.get();

        String address = request().remoteAddress();

        if (params.level.equals("SHOUT")) {
            Logger.error("[WTF 101 from {}, {}, {}] {}", params.email, address, params.userAgent, params.message);
        }
        else {
            Logger.info("[{} from {}, {}, {}] {}", params.level, params.email, address, params.userAgent, params.message);
        }
        return true;
    }

}
