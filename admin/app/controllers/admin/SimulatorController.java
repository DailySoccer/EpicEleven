package controllers.admin;

import model.Global;
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

    public static Result time() {
        return ok(Global.currentTimeString());
    }

    public static Result start() {

        boolean wasResumed = OptaSimulator.start();

        if (wasResumed) {
            FlashMessage.success("Simulator resumed");
        }
        else {
            FlashMessage.success("Simulator started");
        }

        return redirect(routes.SimulatorController.index());
    }

    public static Result pause() {
        OptaSimulator.pause();
        FlashMessage.success("Simulator paused");
        return redirect(routes.SimulatorController.index());
    }

    public static Result nextStep() {
        OptaSimulator.nextStep();
        FlashMessage.success("Simulator next step");
        return redirect(routes.SimulatorController.index());
    }

    public static class GotoSimParams {
        @Constraints.Required
        @Formats.DateTime (pattern = "yyyy-MM-dd'T'HH:mm")
        public Date date;
    }

    public static Result gotoNextPause() {

        Form<GotoSimParams> gotoForm = form(GotoSimParams.class).bindFromRequest();

        if (!gotoForm.hasErrors()) {
            GotoSimParams params = gotoForm.get();
            OptaSimulator.addPause(params.date);
            FlashMessage.success("Pause added. Press play to continue.");
        } else {
            FlashMessage.danger("Wrong button pressed");
        }

        return redirect(routes.SimulatorController.index());
    }

    public static Result reset(){
        OptaSimulator.reset();
        FlashMessage.success("Simulator reset");
        return redirect(routes.SimulatorController.index());
    }

}
