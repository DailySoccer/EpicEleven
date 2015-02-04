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

        return views.html.simulatorbar.render(state.isNotNull(), state.isPaused,
                                              state.getCurrentDateFormatted(),
                                              state.getPauseDateFormatted(),
                                              state.speedFactor);
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
        Model.getDailySoccerActors().tellToActor("SimulatorActor", "NextStep");
        return ok();
    }

    public static Result reset() {
        Model.getDailySoccerActors().tellToActor("SimulatorActor", "Reset");
        return ok();
    }

    public static Result setSpeed(int simSpeed) {
        Model.getDailySoccerActors().tellToActor("SimulatorActor", "SetSpeedFactor");
        return ok();
    }

    public static Result getSimulatorState() {
        SimulatorActor.SimulatorState state = (SimulatorActor.SimulatorState)Model.getDailySoccerActors()
                                                .tellToActorAwaitResult("SimulatorActor", "GetSimulatorState");

        return new ReturnHelper(state).toResult();
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

    public static Result isSimulatorActivated() {
        // Puesto que se llamara desde el cliente en un dominio distinto, tenemos que poner el CORS
        response().setHeader("Access-Control-Allow-Origin", "*");
        return new ReturnHelper(ImmutableMap.of("simulator_activated", OptaSimulator.isCreated())).toResult();
    }
}
