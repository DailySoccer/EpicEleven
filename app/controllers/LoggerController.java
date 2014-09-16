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
        public String errorMessage;
    }
    public static Result logPost() {
        Form<Params> errorForm = form(Params.class).bindFromRequest();
        Params params = errorForm.get();
        Logger.error("WTF 101: Client exception: {}", params.level);
        Logger.error("--------------------------");
        Logger.error(params.errorMessage);
        Logger.error("--------------------------");
        return ok("Stacktrace logged");
    }

}
