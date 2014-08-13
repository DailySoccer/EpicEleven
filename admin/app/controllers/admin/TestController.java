package controllers.admin;

import play.mvc.Controller;
import play.mvc.Result;

import java.util.Date;

public class TestController extends Controller {
    static public Result start() {
        if (!OptaSimulator.isCreated())
            OptaSimulator.init();

        OptaSimulator.instance().reset(false);
        return ok();
    }

    static public Result gotoDate(Long timestamp) {
        Date date = new Date(timestamp);

        if (!OptaSimulator.isCreated())
            OptaSimulator.init();

        OptaSimulator.instance().gotoDate(date);
        return ok();
    }
}
