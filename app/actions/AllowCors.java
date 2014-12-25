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
        public play.libs.F.Promise<play.mvc.Result> call(Http.Context context) throws Throwable {

            String origin = getOrigin(context.request());

            // Cuando estamos en el mismo dominio (localhost:9000 o dailysoccer-staging), no recibimos origin.
            // La header "Access-Control-Allow-Credentials" no nos hace falta ponerla a true pq no usamos cookies.
            if (origin != null && isWhiteListed(origin)) {

                context.response().setHeader("Access-Control-Allow-Origin", origin);

                // Necesitamos que se pueda acceder a la version del servidor, mandada desde un filtro global en Global
                context.response().setHeader("Access-Control-Expose-Headers", "Release-Version");
            }

            return delegate.call(context);
        }
    }

    public static void preFlight(Http.Request request, Http.Response response) {

        String origin = getOrigin(request);

        if (origin != null && isWhiteListed(origin)) {

            response.setHeader("Access-Control-Allow-Origin", origin);
            response.setHeader("Access-Control-Allow-Methods", "GET, POST");

            // Aceptamos nuestro X-SESSION-TOKEN como custom header
            response.setHeader("Access-Control-Allow-Headers", "accept, origin, Content-type, x-json, x-prototype-version, x-requested-with, X-SESSION-TOKEN");

            // Al poner esto, el preflight se hace 1 vez y no se vuelve a hacer hasta que pase el tiempo.
            response.setHeader("Access-Control-Max-Age", String.valueOf(3600));
        }
    }

    private static String getOrigin(Http.Request request) {
        String[] origin = request.headers().get("Origin");

        return origin != null && origin[0] != null? origin[0] : null;
    }

    private static boolean isWhiteListed(String origin) {
        /*
        if (origin.contains("localhost") || origin.contains("127.0.0.1") ||
            origin.equals("epiceleven.com")) {
        }
        */
        return true;
    }
}
