package controllers;

import actions.AllowCors;
import play.Logger;
import play.mvc.Controller;
import play.mvc.Result;

@AllowCors.Origin
public class LoggerController extends Controller {

    public static Result postLog() {
        Logger.error("WTF 101: Client exception:");
        Logger.error("--------------------------");
        Logger.error(request().body().asText());
        Logger.error("--------------------------");
        return ok("Stacktrace logged");
    }

}
