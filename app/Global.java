import model.Model;
import play.*;
import play.mvc.Action;
import play.mvc.Http;

import java.lang.reflect.Method;

// http://www.playframework.com/documentation/2.2.x/JavaGlobal
public class Global extends GlobalSettings {

    public void onStart(Application app) {
        Logger.info("Application has started");

        Model.init();
    }

    public void onStop(Application app) {
        Logger.info("Application shutdown...");

        Model.shutdown();
    }
}