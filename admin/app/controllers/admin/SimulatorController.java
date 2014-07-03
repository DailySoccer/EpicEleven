package controllers.admin;

import play.data.Form;
import play.data.format.Formats;
import play.data.validation.Constraints;
import play.mvc.Controller;
import play.mvc.Result;

import java.util.Date;

import static play.data.Form.form;

public class SimulatorController extends Controller {

    public static Result index() {
        return ok(views.html.simulator.render());
    }

    public static Result start() {

        boolean wasResumed = OptaSimulator.start(0L, System.currentTimeMillis(), null);

        if (wasResumed) {
            FlashMessage.success("Simulator resumed");
        }
        else {
            FlashMessage.success("Simulator started");
        }

        return ok(views.html.simulator.render());
    }

    public static Result pause() {
        OptaSimulator.pause();
        FlashMessage.success("Simulator paused");
        return ok(views.html.simulator.render());
    }

    public static Result next() {
        /*
        if (OptaSimulator.existsInstance()) {
            OptaSimulator mySimulator = OptaSimulator.getInstance();
            mySimulator.next();
            FlashMessage.success("Simulator resumed");
        } else {
            FlashMessage.danger("Simulator not running");
        }
        */

        return ok(views.html.simulator.render());
    }

    public static class GotoSimParams {
        @Constraints.Required
        @Formats.DateTime (pattern = "yyyy-MM-dd'T'HH:mm")
        public Date date;
    }

    public static Result gotoNextPause(){
        /*
        Form<GotoSimParams> gotoForm = form(GotoSimParams.class).bindFromRequest();
        if (!gotoForm.hasErrors()){
            GotoSimParams params = gotoForm.get();
            OptaSimulator mySimulator = OptaSimulator.getInstance();
            mySimulator.addPause(params.date);
            FlashMessage.success("Simulator going");
        } else {
            FlashMessage.danger("Wrong button pressed");
        }
        */

        return ok(views.html.simulator.render());
    }

    public static Result reset(){
        OptaSimulator.reset();
        FlashMessage.success("Simulator reset");
        return ok(views.html.simulator.render());
    }

}
