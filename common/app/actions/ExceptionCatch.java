package actions;

import play.mvc.Action;
import play.mvc.Http;
import play.libs.F.Promise;

public class ExceptionCatch extends Action.Simple {
    @Override
    public play.libs.F.Promise<play.mvc.Result> call(Http.Context context) throws Throwable {

        Promise<play.mvc.Result> promise = null;

        try {
            promise = delegate.call(context);
        }
        catch(Exception e) {
            play.Logger.error("WTF 10001: Server Exception", e);
            promise = Promise.<play.mvc.Result>pure(internalServerError());
        }

        return promise;
    }
}

