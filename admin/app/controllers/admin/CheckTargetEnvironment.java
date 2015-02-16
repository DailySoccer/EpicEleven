package actions;

import model.Model;
import play.Logger;
import play.libs.F;
import play.mvc.Action;
import play.mvc.Http;
import play.mvc.Result;
import play.mvc.With;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@With(CheckTargetEnvironment.CheckTargetEnviromentAction.class)
@Target({ ElementType.TYPE, ElementType.METHOD })
@Retention(RetentionPolicy.RUNTIME)
public @interface CheckTargetEnvironment {
    public class CheckTargetEnviromentAction extends Action<CheckTargetEnvironment> {
        public play.libs.F.Promise<play.mvc.Result>  call(Http.Context ctx) throws Throwable {

            if (!Model.isLocalHostTargetEnvironment()) {
                Logger.error("WTF 9265: Se intento hacer una llamada a un enviroment remoto no autorizada" + ctx.request().uri());

                return F.Promise.promise(new F.Function0<Result>() {
                    public Result apply() throws Throwable {
                        return unauthorized();
                    }
                });
            }

            return delegate.call(ctx);
        }
    }
}