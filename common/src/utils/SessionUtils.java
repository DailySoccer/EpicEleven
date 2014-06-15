package utils;

import model.Model;
import model.Session;
import model.User;

import play.Play;
import play.mvc.Http;

public class SessionUtils {

    public static User getUserFromRequest(Http.Request request) {
        String sessionToken = getSessionTokenFromRequest(request);

        if (sessionToken == null)
            return null;

        Session theSession = Model.sessions().findOne("{sessionToken:'#'}", sessionToken).as(Session.class);

        if (theSession == null)
            return null;

        return Model.users().findOne(theSession.userId).as(User.class);
    }

    private static String getSessionTokenFromRequest(Http.Request request) {
        // TODO: Security problem when this gets logged by any server. Move it to the HTTP Basic Auth header
        String sessionToken = request.getQueryString("sessionToken");

        // During development we try to read the sessionToken from a cookie to make it easier
        if (sessionToken == null && Play.isDev()) {
            Http.Cookie theCookie = request.cookie("sessionToken");

            if (theCookie != null)
                sessionToken = theCookie.value();
        }

        return sessionToken;
    }

}
