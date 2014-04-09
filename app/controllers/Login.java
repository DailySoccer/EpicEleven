package controllers;

import model.Model;
import model.Session;
import model.User;
import org.jongo.Jongo;
import org.jongo.MongoCollection;
import play.Play;
import play.data.validation.Constraints.*;
import play.mvc.Controller;
import play.mvc.Http;
import play.mvc.Result;
import play.data.*;
import static play.data.Form.*;
import play.Logger;

import java.util.Date;

public class Login extends Controller {

    // https://github.com/playframework/playframework/tree/master/samples/java/forms
    public static class SignupParams {
        @Required @MinLength(value = 4)
        public String firstName;

        @Required @MinLength(value = 4)
        public String lastName;

        @Required @MinLength(value = 4)
        public String nickName;

        @Required @Email public String email;

        @Required public String password;
    }


    public static class LoginParams {
        @Required public String email;
        @Required public String password;
    }


    public static Result signup() {
        Form<SignupParams> signupForm = form(SignupParams.class).bindFromRequest();

        if (!signupForm.hasErrors()) {
            if(signupForm.field("nickName").valueOr("").equals("admin"))
                signupForm.reject("nickName", "This nickName is already taken");

            if (!isSecurePassword(signupForm.field("password").valueOr("")))
                signupForm.reject("password", "Password is not secure");
        }

        if (!signupForm.hasErrors()) {
            // TODO: Exceptions managing (duplicated key, for instance)
            createUser(signupForm.get());
            return ok();
        }
        else {
            return badRequest(signupForm.errorsAsJson());
        }
    }


    private static void createUser(SignupParams theParams) {
        Jongo jongo = Model.createJongo();

        MongoCollection users = jongo.getCollection("users");
        users.insert(new User(theParams.firstName, theParams.lastName, theParams.nickName,
                              theParams.email, theParams.password));
    }


    // TODO: Todo esto es totalmente incorrecto, es un borrador. Hay que hacer cosas como caducidad de sesiones,
    // confirmacion de la cuenta a traves de email, etc. Hay un monton de notas en Asana
    public static Result login() {
        Form<LoginParams> loginParamsForm = form(LoginParams.class).bindFromRequest();

        if (loginParamsForm.hasErrors())
            return badRequest("TODO");

        LoginParams loginParams = loginParamsForm.get();
        Jongo jongo = Model.createJongo();

        // TODO: Necesitamos sanitizar el email?
        User theUser = jongo.getCollection("users").findOne("{email:'#'}", loginParams.email).as(User.class);

        if (theUser == null)
            return badRequest("TODO");

        // TODO: Password check (SecretKeyFactory), verificar el rendimiento de SecureRandom, ver si podemos quitar
        // el indice unico en las sesiones, caducidad
        String sessionToken = Model.getRandomSessionToken();
        Session newSession = new Session(sessionToken, theUser._id, new Date());
        jongo.getCollection("sessions").insert(newSession);

        // Durante el desarrollo en local, usamos cookies para que sea mas facil debugear
        if (Play.isDev())
            response().setCookie("sessionToken", sessionToken);

        return ok(sessionToken);
    }


    public static Result userProfile() {
        Jongo jongo = Model.createJongo();

        User theUser = getUserFromSession(jongo);

        if (theUser == null)
            return badRequest("TODO");

        return ok(theUser.nickName.toString());
    }


    private static User getUserFromSession(Jongo jongo) {
        String sessionToken = getSessionToken();

        if (sessionToken == null)
            return null;

        MongoCollection sessions = jongo.getCollection("sessions");
        Session theSession = sessions.findOne("{sessionToken:'#'}", sessionToken).as(Session.class);

        if (theSession == null)
            return null;

        return jongo.getCollection("users").findOne(theSession.userId).as(User.class);
    }


    private static String getSessionToken() {
        String sessionToken = request().getQueryString("sessionToken");

        if (sessionToken == null && Play.isDev()) {
            Http.Cookie theCookie = request().cookie("sessionToken");

            if (theCookie != null)
                sessionToken = theCookie.value();
        }

        return sessionToken;
    }


    private static boolean isSecurePassword(String password) {
        return true;
    }
}