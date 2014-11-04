package actions;

import model.User;
import play.Logger;
import play.libs.F;
import play.mvc.Action;
import play.mvc.Http;
import play.mvc.Result;
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
        public play.libs.F.Promise<play.mvc.Result>  call(Http.Context ctx) throws Throwable {

            User theUser = SessionUtils.getUserFromRequest(ctx.request());

            if (theUser == null) {
                Logger.info("UserAuthenticated: Fallo de autentificacion en url " + ctx.request().uri());

                // Tenemos que poner las headers bien para que llegue el 401 al cliente. Si no, el error que le da es como
                // que no tiene Allow
                AllowCors.preFlight(ctx.request(), ctx.response());

                return F.Promise.promise(new F.Function0<Result>() {
                    public Result apply() throws Throwable {
                        return unauthorized();
                    }
                });
            }

            ctx.args.put("User", theUser);

            return delegate.call(ctx);
        }
    }
}
