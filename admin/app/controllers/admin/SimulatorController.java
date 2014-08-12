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

    public static Result switchSimulator() {

        if (OptaSimulator.isCreated())
            OptaSimulator.shutdown();
        else
            OptaSimulator.init();

        return ok();
    }
    public static Result start() {
        OptaSimulator.instance().start();
        return ok();
    }

    public static Result pause() {
        OptaSimulator.instance().pause();
        return ok();
    }

    public static Result nextStep() {
        OptaSimulator.instance().nextStep();
        return ok();
    }

    public static Result reset() {
        OptaSimulator.instance().reset(false);
        return ok();
    }

    public static Result currentDate() {  return ok(getCurrentDate()); }
    public static Result isCreated() { return ok(String.valueOf(isSimulatorCreated()));  }
    public static Result isPaused() { return ok((String.valueOf(isSimulatorPaused())));  }
    public static Result nextStop() { return ok(getNextStop()); }
    public static Result nextStepDescription() {
        return ok(getNextStepDescription());
    }

    public static class GotoSimParams {
        @Constraints.Required
        @Formats.DateTime (pattern = "yyyy-MM-dd'T'HH:mm")
        public Date date;
    }

    public static Result gotoDate() {

        Form<GotoSimParams> gotoForm = form(GotoSimParams.class).bindFromRequest();

        GotoSimParams params = gotoForm.get();
        OptaSimulator.instance().gotoDate(params.date);

        return ok();
    }

    public static String getCurrentDate() {
        return GlobalDate.getCurrentDateString();
    }

    public static String getNextStop() {

        if (isSimulatorCreated()) {
            Date nextStopDate = OptaSimulator.instance().getNextStop();

            if (nextStopDate != null) {
                return GlobalDate.formatDate(nextStopDate);
            }
        }

        return "";
    }

    public static String getNextStepDescription() {
        return isSimulatorCreated()? OptaSimulator.instance().getNextStepDescription() : "";
    }

    public static boolean isSnapshotEnabled() {
        return isSimulatorCreated() && OptaSimulator.instance().isSnapshotEnabled();
    }

    public static boolean isSimulatorPaused() {
        return !isSimulatorCreated() || OptaSimulator.instance().isPaused();
    }

    public static boolean isSimulatorCreated() {
        return OptaSimulator.isCreated();
    }

    @AllowCors.Origin
    public static Result isSimulatorActivated() {
        return new ReturnHelper(ImmutableMap.of("simulator_activated", OptaSimulator.isCreated())).toResult();
    }
}
