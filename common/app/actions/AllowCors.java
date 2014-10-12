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

            String[] origin = context.request().headers().get("Origin");

            // Cuando estamos en el mismo dominio (localhost:9000 o dailysoccer-staging), no recibimos origin.
            // La header "Access-Control-Allow-Credentials" no nos hace falta ponerla a true pq no usamos cookies.
            if (origin != null && origin[0] != null && isWhiteListed(origin[0])) {
                context.response().setHeader("Access-Control-Allow-Origin", origin[0]);
            }

            return delegate.call(context);
        }
    }

    private static boolean isWhiteListed(String origin) {
        /*
        if (host[0].contains("localhost") || host[0].contains("127.0.0.1") ||
            host[0].equals("epiceleven.com")) {
        }
        */
        return true;
    }
}
