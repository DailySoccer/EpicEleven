package actions;

import play.mvc.Action;
import play.mvc.Http;
import play.mvc.With;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;


public class AllowCors {
    @With(CorsAction.class)
    @Target({ ElementType.TYPE, ElementType.METHOD })
    @Retention(RetentionPolicy.RUNTIME)
    public @interface Origin {
        String value() default "*";
    }

    public static class CorsAction extends Action<Origin> {
        @Override
        public play.libs.F.Promise<play.mvc.Result>  call(Http.Context context) throws Throwable {
            context.response().setHeader("Access-Control-Allow-Origin", "*");
            return delegate.call(context);
        }
    }
}
