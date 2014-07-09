package controllers.admin;

import model.GlobalDate;
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

    public static Result currentDate() {
        return ok(GlobalDate.getCurrentDateString());
    }

    public static Result start() {

        boolean wasResumed = OptaSimulator.start();

        return redirect(routes.SimulatorController.index());
    }

    public static Result pause() {
        OptaSimulator.pause();
        return redirect(routes.SimulatorController.index());
    }

    public static Result nextStep() {
        OptaSimulator.nextStep();
        return redirect(routes.SimulatorController.index());
    }

    public static Result isPaused() {
        return ok(((Boolean)OptaSimulator.isPaused()).toString());
    }

    public static Result getNextStop() {
        return ok(OptaSimulator.getNextStop());
    }

    public static Result nextStepDescription() {
        return ok(OptaSimulator.getNextStepDescription());
    }

    public static class GotoSimParams {
        @Constraints.Required
        @Formats.DateTime (pattern = "yyyy-MM-dd'T'HH:mm")
        public Date date;
    }

    public static Result gotoDate() {

        Form<GotoSimParams> gotoForm = form(GotoSimParams.class).bindFromRequest();

        GotoSimParams params = gotoForm.get();
        OptaSimulator.gotoDate(params.date);
        FlashMessage.success("Pause added: "+params.date.toString());
        boolean wasResumed = OptaSimulator.start();

        return redirect(routes.SimulatorController.index());
    }

    public static Result reset(){
        OptaSimulator.reset();
        FlashMessage.success("Simulator reset");
        FlashMessage.info("The DB was totally erased. If you press Start now, the simulation will start at the beginning of time (the first file Opta sent).");
        return redirect(routes.SimulatorController.index());
    }

}
