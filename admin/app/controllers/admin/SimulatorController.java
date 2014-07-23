package controllers.admin;

import model.GlobalDate;
import model.Snapshot;
import play.Logger;
import play.data.Form;
import play.data.format.Formats;
import play.data.validation.Constraints;
import play.mvc.Controller;
import play.mvc.Result;


import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

import static play.data.Form.form;

public class SimulatorController extends Controller {

    public static Result index() {
        return ok(views.html.simulator.render());
    }

    public static Result currentDate() {
        return ok(new SimpleDateFormat("yyyy/MM/dd HH:mm:ss").format(OptaSimulator.getCurrentDate()));
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

    public static Result replayLast() {
        OptaSimulator.reset();
        OptaSimulator.useSnapshot();
        return redirect(routes.SimulatorController.index());
    }

    public static Result snapshot() {
        Snapshot.create();

        return redirect(routes.SimulatorController.index());
    }

    public static Result snapshotDB() {
        Snapshot.createInDB();

        return redirect(routes.SimulatorController.index());
    }

    public static Result snapshotDump() {
        ProcessBuilder pb = new ProcessBuilder("./snapshot_dump.sh", "snapshot000");
        String pwd = pb.environment().get("PWD");
        ProcessBuilder data = pb.directory(new File(pwd+"/data"));
        try {
            Process p = data.start();
        } catch (IOException e) {
            Logger.error("WTF 4264", e);
        }
        return ok();
    }

    public static Result snapshotRestore() {
        ProcessBuilder pb = new ProcessBuilder("./snapshot_restore.sh", "snapshot000");
        String pwd = pb.environment().get("PWD");
        ProcessBuilder data = pb.directory(new File(pwd+"/data"));
        try {
            Process p = data.start();
        } catch (IOException e) {
            Logger.error("WTF 1124", e);
        }
        return ok();
    }

}
