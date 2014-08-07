package controllers.admin;

import actions.AllowCors;
import com.google.common.collect.ImmutableMap;
import model.GlobalDate;
import play.data.Form;
import play.data.format.Formats;
import play.data.validation.Constraints;
import play.mvc.Controller;
import play.mvc.Result;
import utils.ReturnHelper;

import java.util.Date;

import static play.data.Form.form;

public class SimulatorController extends Controller {

    public static Result currentDate() {
        return ok(GlobalDate.formatDate(OptaSimulator.getCurrentDate()));
    }

    public static Result start() {

        boolean wasResumed = OptaSimulator.start();

        return ok();
    }

    public static Result pause() {
        OptaSimulator.pause();
        return ok();
    }

    public static Result nextStep() {
        OptaSimulator.nextStep();
        return ok();
    }

    public static Result isRunning() {
        return ok(((Boolean)!OptaSimulator.isFinished()).toString());
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
        OptaSimulator.start();

        return ok();
    }

    public static Result reset(){
        OptaSimulator.reset();
        return ok();
    }

    @AllowCors.Origin
    public static Result isSimulatorActivated() {
        return new ReturnHelper(ImmutableMap.of(
                "simulator_activated", OptaSimulator.isCreated()
        )).toResult();
    }
}
