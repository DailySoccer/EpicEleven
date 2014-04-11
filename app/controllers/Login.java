package controllers;

import actions.CorsComposition;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mongodb.MongoException;
import model.Model;
import model.Session;
import model.User;
import org.jongo.MongoCollection;
import play.Play;
import play.data.validation.Constraints.*;
import play.libs.Crypto;
import play.libs.Json;
import play.mvc.Controller;
import play.mvc.Http;
import play.mvc.Result;
import play.data.*;
import static play.data.Form.*;
import play.Logger;

import java.util.Date;

@CorsComposition.Cors
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
        SignupParams params = null;
        ObjectNode result = Json.newObject();

        if (!signupForm.hasErrors()) {
            params = signupForm.get();
            User dup = Model.users().findOne("{ $or: [ {email:'#'}, {nickName:'#'} ] }", params.email, params.nickName).as(User.class);

            if (dup != null) {
                if (dup.email.equals(params.email))
                    signupForm.reject("email", "This email is already taken");

                if (dup.nickName.equals(params.nickName))
                    signupForm.reject("nickName", "This nickName is already taken");
            }

            if (!isSecurePassword(params.password))
                signupForm.reject("password", "Password is not secure");
        }

        if (!signupForm.hasErrors()) {
            if (!createUser(params))
                signupForm.reject("generalError", "General error: Try again please");
        }

        if (signupForm.hasErrors())
            return badRequest(signupForm.errorsAsJson());
        else
            return ok();
    }


    private static boolean createUser(SignupParams theParams) {
        boolean bRet = true;

        // Puede ocurrir que salte una excepcion por duplicidad, no seria un error de programacion puesto que, aunque
        // comprobamos si el email o nickname estan duplicados antes de llamar aqui, es posible que se creen en
        // paralelo. Por esto, la vamos a controlar explicitamente
        try {
            MongoCollection users = Model.jongo().getCollection("users");
            users.insert(new User(theParams.firstName, theParams.lastName, theParams.nickName,
                                  theParams.email, theParams.password));
        } catch (MongoException exc) {
            Logger.error("createUser:", exc);
            bRet = false;
        }

        return bRet;
    }


    // TODO: Todo esto es totalmente incorrecto, es un borrador. Hay que hacer cosas como caducidad de sesiones,
    // confirmacion de la cuenta a traves de email, etc. Hay un monton de notas en Asana
    public static Result login() {
        Form<LoginParams> loginParamsForm = form(LoginParams.class).bindFromRequest();

        if (loginParamsForm.hasErrors())
            return badRequest("TODO");

        LoginParams loginParams = loginParamsForm.get();

        // TODO: Necesitamos sanitizar el email?
        User theUser = Model.users().findOne("{email:'#'}", loginParams.email).as(User.class);

        if (theUser == null)
            return badRequest("TODO");

        // TODO: Password check, caducidad
        String sessionToken = Crypto.generateSignedToken();
        Session newSession = new Session(sessionToken, theUser._id, new Date());
        Model.sessions().insert(newSession);

        // Durante el desarrollo en local, usamos cookies para que sea mas facil debugear
        if (Play.isDev())
            response().setCookie("sessionToken", sessionToken);

        return ok(sessionToken);
    }


    public static Result userProfile() {

        User theUser = getUserFromSession();

        if (theUser == null)
            return badRequest("TODO");

        return ok(theUser.nickName.toString());
    }


    private static User getUserFromSession() {
        String sessionToken = getSessionToken();

        if (sessionToken == null)
            return null;

        Session theSession = Model.sessions().findOne("{sessionToken:'#'}", sessionToken).as(Session.class);

        if (theSession == null)
            return null;

        return Model.users().findOne(theSession.userId).as(User.class);
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