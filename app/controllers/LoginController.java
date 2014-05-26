package controllers;

import actions.AllowCors;
import play.Logger;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mongodb.MongoException;
import model.ClientError;
import model.Model;
import utils.ReturnHelper;
import model.Session;
import model.User;
import play.Play;
import play.data.validation.Constraints.*;
import play.libs.Crypto;
import play.mvc.Controller;
import play.mvc.Result;
import play.data.*;
import utils.SessionUtils;

import static play.data.Form.*;


import java.util.Date;

@AllowCors.Origin
public class LoginController extends Controller {

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
                signupForm.reject("error", "General error: Try again please");
        }

        JsonNode result = signupForm.errorsAsJson();

        if (!signupForm.hasErrors()) {
            result = new ObjectMapper().createObjectNode().put("result", "ok");
        }

        return new ReturnHelper(!signupForm.hasErrors(), result).toResult();
    }


    private static boolean createUser(SignupParams theParams) {
        boolean bRet = true;

        // Puede ocurrir que salte una excepcion por duplicidad. No seria un error de programacion puesto que, aunque
        // comprobamos si el email o nickname estan duplicados antes de llamar aqui, es posible que se creen en
        // paralelo. Por esto, la vamos a controlar explicitamente
        try {
            Model.users().insert(new User(theParams.firstName, theParams.lastName, theParams.nickName,
                                          theParams.email, theParams.password));
        } catch (MongoException exc) {
            Logger.error("createUser: ", exc);
            bRet = false;
        }

        return bRet;
    }


    // TODO: Todo esto es totalmente incorrecto, es un borrador. Hay que hacer cosas como caducidad de sesiones,
    // confirmacion de la cuenta a traves de email, etc. Hay un monton de notas en Asana.
    public static Result login() {
        Form<LoginParams> loginParamsForm = form(LoginParams.class).bindFromRequest();
        ReturnHelper returnHelper = new ReturnHelper();

        if (!loginParamsForm.hasErrors()) {
            LoginParams loginParams = loginParamsForm.get();

            // TODO: Necesitamos sanitizar el email?
            User theUser = Model.users().findOne("{email:'#'}", loginParams.email).as(User.class);

            if (theUser == null || !isPasswordCorrect(theUser, loginParams.password)) {
                loginParamsForm.reject("email", "email or password incorrect");
                returnHelper.setKO(loginParamsForm.errorsAsJson());
            }
            else {
                String sessionToken = Crypto.generateSignedToken();
                Session newSession = new Session(sessionToken, theUser._id, new Date());
                Model.sessions().insert(newSession);

                // Durante el desarrollo en local, usamos cookies para que sea mas facil debugear
                if (Play.isDev())
                    response().setCookie("sessionToken", sessionToken);

                returnHelper.setOK(newSession);
            }
        }

        return returnHelper.toResult();
    }


    public static Result getUserProfile() {
        ReturnHelper returnHelper = new ReturnHelper();
        User theUser = SessionUtils.getUserFromRequest(request());

        if (theUser == null) {
            returnHelper.setKO(new ClientError("User not found", "Check your sessionToken"));
        } else {
            returnHelper.setOK(theUser);
        }

        return returnHelper.toResult();
    }


    private static boolean isPasswordCorrect(User theUser, String password) {
        return true;
    }

    private static boolean isSecurePassword(String password) {
        return true;
    }
}