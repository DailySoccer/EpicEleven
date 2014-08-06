package controllers;

import actions.AllowCors;
import actions.UserAuthenticated;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mongodb.MongoException;
import controllers.admin.OptaSimulator;
import model.*;
import play.Logger;
import play.Play;
import play.data.Form;
import play.data.validation.Constraints.Email;
import play.data.validation.Constraints.MinLength;
import play.data.validation.Constraints.Required;
import play.libs.Crypto;
import play.mvc.Controller;
import play.mvc.Result;
import utils.ReturnHelper;

import java.util.Date;

import static play.data.Form.form;

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
                if (Play.isDev()) {
                    Logger.info("Estamos en desarrollo: El email sera el sessionToken");

                    // Durante el desarrollo en local usamos cookies y el email como sessionToken para que sea mas facil debugear
                    // por ejemplo usando Postman
                    response().setCookie("sessionToken", theUser.email);

                    returnHelper.setOK(new Session(theUser.email, theUser.userId, new Date()));

                    // Cuando estamos en desarrollo y el simulador está activo mandaremos información extra...
                    if (OptaSimulator.isCreated()) {
                        return returnHelper.toResult(JsonViews.Simulation.class);   // <<==================== RETURN
                    }
                }
                else {
                    String sessionToken = Crypto.generateSignedToken();
                    Session newSession = new Session(sessionToken, theUser.userId, new Date());
                    Model.sessions().insert(newSession);

                    returnHelper.setOK(newSession);
                }
            }
        }

        return returnHelper.toResult();
    }

    @UserAuthenticated
    public static Result getUserProfile() {
        ReturnHelper returnHelper = new ReturnHelper();
        User theUser = (User)ctx().args.get("User");

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