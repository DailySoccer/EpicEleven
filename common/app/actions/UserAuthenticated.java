package actions;

import model.User;
import play.Logger;
import play.libs.F;
import play.mvc.Action;
import play.mvc.Http;
import play.mvc.SimpleResult;
import play.mvc.With;
import utils.SessionUtils;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;


@With(UserAuthenticated.UserAuthenticatedAction.class)
@Target({ ElementType.TYPE, ElementType.METHOD })
@Retention(RetentionPolicy.RUNTIME)
public @interface UserAuthenticated {
    public class UserAuthenticatedAction extends Action<UserAuthenticated> {
        public F.Promise<SimpleResult> call(Http.Context ctx) throws Throwable {

            User theUser = SessionUtils.getUserFromRequest(ctx.request());

            if (theUser == null) {
                Logger.info("UserAuthenticated failed: " + ctx);
                return F.Promise.promise(new F.Function0<SimpleResult>() {
                    @Override
                    public SimpleResult apply() throws Throwable {
                        return badRequest();
                    }
                });
            }

            ctx.args.put("User", theUser);

            return delegate.call(ctx);
        }
    }
}
