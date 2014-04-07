import com.mongodb.*;
import model.Model;
import play.*;

// http://www.playframework.com/documentation/2.2.x/JavaGlobal
public class Global extends GlobalSettings {

    public void onStart(Application app) {
        Logger.info("Application has started");

        Model.InitConnection();
    }

    public void onStop(Application app) {
        Logger.info("Application shutdown...");

        Model.ShutdownConnection();
    }
}