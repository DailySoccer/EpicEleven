package controllers.admin;

import model.Model;
import model.PrizeType;
import model.TemplateContest;
import org.joda.time.DateTime;
import play.mvc.Controller;
import play.mvc.Result;

import java.util.ArrayList;
import java.util.Arrays;
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

    static public Result createContests(int mockIndex){
        TemplateContest templateContest;

        switch(mockIndex) {
            case 0:
                Model.templateContests().insert(new TemplateContest(
                        "jue., 12 jun.", 1, 200, 90000, 0, PrizeType.FREE,
                        new DateTime(2014, 6, 12, 0, 0).toDate(),
                        new ArrayList<String>(Arrays.asList("731769", "731774"))) //ESP-NLD, ENG-ITA
                );
/*
                Model.templateContests().insert(new TemplateContest(
                        "jue., 12 jun.", 1, 200, 90000, 0, PrizeType.FREE,
                        new DateTime(2014, 6, 12, 0, 0).toDate(),
                        new ArrayList<String>(Arrays.asList("731767", "731776", "731793", "731813"))) //ESP-NLD, ENG-ITA
                );
*/
                break;
            case 1:
                break;
            default:
        }


        return ok("OK");
    }

}
