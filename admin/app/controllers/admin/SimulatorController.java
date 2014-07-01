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

    public static Result startSimulator(){
        if (OptaSimulator.stoppedInstance()){
            OptaSimulator mySimulator = OptaSimulator.getInstance();
            mySimulator.launch(0L, System.currentTimeMillis(), 0, false, true, null);
            mySimulator.start();
            mySimulator.pause();
            FlashMessage.success("Simulator started");
            return ok(views.html.simulator.render());
            //return ok("Simulator started");
        }
        FlashMessage.warning("Simulator already started");
        return ok(views.html.simulator.render());
        //return ok("Simulator already started");

    }

    public static Result launchSimulator(){
        OptaSimulator mySimulator = OptaSimulator.getInstance();
        if (OptaSimulator.stoppedInstance()){
            mySimulator.launch(0L, System.currentTimeMillis(), 0, false, true, null);
            //Launch as a Thread, runs and parses all documents as fast as possible
            mySimulator.start();
            FlashMessage.success("Simulator started");
            return ok(views.html.simulator.render());
            //return ok("Simulator started");
        }
        FlashMessage.warning("Simulator already started");
        return ok(views.html.simulator.render());
        //return ok("Simulator already started");
    }

    public static Result pauseSimulator(){
        OptaSimulator.getInstance().pause();
        FlashMessage.success("Simulator paused");
        return ok(views.html.simulator.render());
        //return ok("Simulator paused");
    }

    public static Result resumeSimulator(){
        OptaSimulator mySimulator = OptaSimulator.getInstance();
        mySimulator.resumeLoop();
        FlashMessage.success("Simulator resumed");
        return ok(views.html.simulator.render());
        //return ok("Simulator resumed");
    }

    public static Result simulatorNext(){
        if (OptaSimulator.existsInstance()) {
            OptaSimulator mySimulator = OptaSimulator.getInstance();
            mySimulator.next();
            FlashMessage.success("Simulator resumed");
            return ok(views.html.simulator.render());
            //return ok("Simulator next step");
        } else {
            FlashMessage.danger("Simulator not running");
            return ok(views.html.simulator.render());
            //return ok("Simulator not running");
        }
    }

    public static class GotoSimParams {
        @Constraints.Required
        @Formats.DateTime (pattern = "yyyy-MM-dd'T'HH:mm")
        public Date date;
    }

    public static Result gotoSimulator(){
        Form<GotoSimParams> gotoForm = form(GotoSimParams.class).bindFromRequest();
        if (!gotoForm.hasErrors()){
            GotoSimParams params = gotoForm.get();
            OptaSimulator mySimulator = OptaSimulator.getInstance();
            mySimulator.addPause(params.date);
            FlashMessage.success("Simulator going");
            return ok(views.html.simulator.render());
        } else {
            FlashMessage.danger("Wrong button pressed");
            return ok(views.html.simulator.render());
        }
    }

    public static Result stopSimulator(){
        OptaSimulator mySimulator = OptaSimulator.getInstance();
        mySimulator.halt();
        FlashMessage.success("Simulator stopped");
        return ok(views.html.simulator.render());
        //return ok("Simulator stopped");
    }

}
