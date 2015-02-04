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

        return views.html.simulatorbar.render(state.isInit(), state.isPaused,
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

    public static Result setSpeed(int speedFactor) {
        Model.getDailySoccerActors().tellToActor("SimulatorActor", new SimulatorActor.SpeedFactorMsg(speedFactor));
        return ok();
    }

    public static Result gotoDate() {
        GotoSimParams params = form(GotoSimParams.class).bindFromRequest().get();

        Date date = new DateTime(params.date).withZoneRetainFields(DateTimeZone.UTC).toDate();
        Model.getDailySoccerActors().tellToActor("SimulatorActor", new SimulatorActor.GotoDateMsg(date));

        return ok();
    }


    public static Result getSimulatorState() {
        // Puesto que se llamara tambien desde el cliente en un dominio distinto, tenemos que poner el CORS
        response().setHeader("Access-Control-Allow-Origin", "*");

        SimulatorActor.SimulatorState state = (SimulatorActor.SimulatorState)Model.getDailySoccerActors()
                                               .tellToActorAwaitResult("SimulatorActor", "GetSimulatorState");

        return new ReturnHelper(state).toResult();
    }

    public static class GotoSimParams {
        @Constraints.Required
        @Formats.DateTime (pattern = "yyyy-MM-dd'T'HH:mm")
        public Date date;
    }
}
