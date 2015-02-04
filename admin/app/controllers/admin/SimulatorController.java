package controllers.admin;

import actors.SimulatorActor;
import com.google.common.collect.ImmutableMap;
import model.GlobalDate;
import model.Model;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import play.data.Form;
import play.data.format.Formats;
import play.data.validation.Constraints;
import play.mvc.Controller;
import play.mvc.Result;
import play.twirl.api.Html;
import utils.ReturnHelper;

import java.util.Date;

import static play.data.Form.form;

public class SimulatorController extends Controller {

    public static Html simulatorBar() {
        SimulatorActor.SimulatorState state = (SimulatorActor.SimulatorState)Model.getDailySoccerActors()
                                              .tellToActorAwaitResult("SimulatorActor", "GetSimulatorState");

        return views.html.simulatorbar.render(state.isNotNull(), state.isPaused, state.simulationDate, state.pauseDate, "TODO");
    }

    public static Result initShutdown() {

        Model.getDailySoccerActors().tellToActor("SimulatorActor", "InitShutdown");

        return ok();
    }
    public static Result pauseResume() {

        Model.getDailySoccerActors().tellToActor("SimulatorActor", "PauseResume");

        return ok();
    }

    public static Result nextStep() {
        /*
        if (OptaSimulator.isCreated()) {
            OptaSimulator.instance().nextStep(OptaSimulator.MAX_SPEED);
        }
        */
        return ok();
    }

    public static Result reset() {
        /*
        if (OptaSimulator.isCreated()) {
            OptaSimulator.instance().reset();
        }
        */
        return ok();
    }

    public static Result currentDate() {  return ok(getCurrentDate()); }
    public static Result isCreated() { return ok(String.valueOf(isSimulatorCreated()));  }
    public static Result isPaused() { return ok((String.valueOf(isSimulatorPaused())));  }
    public static Result nextStop() { return ok(getNextStop()); }
    public static Result nextStepDescription() {
        return ok(getNextStepDesc());
    }

    public static class GotoSimParams {
        @Constraints.Required
        @Formats.DateTime (pattern = "yyyy-MM-dd'T'HH:mm")
        public Date date;
    }

    public static Result gotoDate() {

        Form<GotoSimParams> gotoForm = form(GotoSimParams.class).bindFromRequest();

        GotoSimParams params = gotoForm.get();
        OptaSimulator.instance().gotoDate(new DateTime(params.date).withZoneRetainFields(DateTimeZone.UTC).toDate());

        return ok();
    }

    public static String getCurrentDate() {
        return GlobalDate.getCurrentDateString();
    }

    public static String getNextStop() {

        /*
        if (isSimulatorCreated()) {
            Date nextStopDate = OptaSimulator.instance().getNextStop();

            if (nextStopDate != null) {
                return GlobalDate.formatDate(nextStopDate);
            }
        }
        */

        return "";
    }

    public static String getNextStepDesc() {
        //return isSimulatorCreated()? OptaSimulator.instance().getNextStepDesc() : "";
        return "";
    }

    public static boolean isSimulatorPaused() {
        //return !isSimulatorCreated() || OptaSimulator.instance().isPaused();
        return false;
    }

    public static boolean isSimulatorCreated() {
        //return OptaSimulator.isCreated();
        return true;
    }

    public static Result isSimulatorActivated() {
        // Puesto que se llamara desde el cliente en un dominio distinto, tenemos que poner el CORS
        response().setHeader("Access-Control-Allow-Origin", "*");
        return new ReturnHelper(ImmutableMap.of("simulator_activated", OptaSimulator.isCreated())).toResult();
    }

    public static Result setSpeed(int simSpeed) {
        OptaSimulator.instance().setSpeedFactor(simSpeed);
        return ok();
    }

    public static Result getSpeed() {
        if (OptaSimulator.isCreated())
            return ok(String.valueOf(OptaSimulator.instance().getSpeedFactor()));
        else
            return ok("-1");
    }
}
