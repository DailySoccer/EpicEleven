import model.Model;
import play.*;
import play.api.mvc.EssentialFilter;
import play.filters.gzip.GzipFilter;

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

    public <T extends EssentialFilter> Class<T>[] filters() {
        if (Play.isProd())
            return new Class[]{GzipFilter.class};
        return new Class[] {};
    }
}