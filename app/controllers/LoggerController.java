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
        public String errorMessage;
    }

    public static Result log() {

        Form<Params> errorForm = form(Params.class).bindFromRequest();
        Params params = errorForm.get();

        Logger.error("\n[Client {}] WTF 101: Client exception at {}:\n" +
                     "[Client {}]--------------------------\n" +
                     "[Client {}] {}\n" +
                     "[Client {}]--------------------------",
                     params.level, params.time,
                     params.level,
                     params.level, params.errorMessage,
                     params.level);

        return ok("");
    }

}
