package controllers.admin;

import play.mvc.Controller;
import play.mvc.Result;

import java.util.Date;
import java.util.GregorianCalendar;
import java.util.TimeZone;

public class TestController extends Controller {

    static public Result start() {
        if (!OptaSimulator.isCreated())
            OptaSimulator.init();

        OptaSimulator.instance().reset(false);
        return ok("OK");
    }



    static public Result gotoDateTest(int year, int month, int day, int hour, int minute) {

        if (!OptaSimulator.isCreated())
            OptaSimulator.init();

        GregorianCalendar myDate = new GregorianCalendar(year, month-1, day, hour, minute);

        myDate.setTimeZone(TimeZone.getTimeZone("UTC"));

        OptaSimulator.instance().gotoDate(myDate.getTime());

        return ok("OK");
    }


    static public Result gotoDate(Long timestamp) {
        Date date = new Date(timestamp);

        if (!OptaSimulator.isCreated())
            OptaSimulator.init();

        OptaSimulator.instance().gotoDate(date);
        return ok("OK");
    }

    static public Result initialSetup() {
        DashboardController.initialSetup();
        return ok("OK");
    }

    static public Result importEverything() {
        DashboardController.importEverything();
        return ok("OK");
    }

    static public Result getCurrentDate() {
        return ok(SimulatorController.getCurrentDate());
    }
}
