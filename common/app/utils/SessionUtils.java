package utils;

import model.Model;
import model.Session;
import model.User;
import play.Logger;
import play.Play;
import play.mvc.Http;

public class SessionUtils {

    public static User getUserFromRequest(Http.Request request) {
        String sessionToken = getSessionTokenFromRequest(request);

        if (sessionToken == null) {
            Logger.debug("SessionUtils.getUserFromRequest: No hay session token");
            return null;
        }

        // En desarrollo, el sessionToken hace referencia al email
        if (Play.isDev()) {
            return User.findByEmail(sessionToken);
        }

        Session theSession = Model.sessions().findOne("{sessionToken:'#'}", sessionToken).as(Session.class);

        if (theSession == null) {
            return null;
        }
        else {
            return Model.users().findOne(theSession.userId).as(User.class);
        }
    }

    private static String getSessionTokenFromRequest(Http.Request request) {

        // Usamos una custom header para no usar cookies y evitar CSRFs
        String sessionToken = null;
        String[] sessionTokenValues = request.headers().get("X-SESSION-TOKEN");

        if (sessionTokenValues != null && sessionTokenValues.length == 1) {
            sessionToken = sessionTokenValues[0];
        }

        // Durante el desarrollo, intentamos leer la cookie para que sea mas facil debugear
        if (sessionToken == null && Play.isDev()) {
            Http.Cookie theCookie = request.cookie("sessionToken");

            if (theCookie != null) {
                sessionToken = theCookie.value();
            }
        }

        return sessionToken;
    }
}
