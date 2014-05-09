package actions;

import play.libs.F;
import play.mvc.Action;
import play.mvc.Http;
import play.mvc.SimpleResult;
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
        public F.Promise<SimpleResult> call(Http.Context context) throws Throwable {
            context.response().setHeader("Access-Control-Allow-Origin", "*");
            return delegate.call(context);
        }
    }
}
