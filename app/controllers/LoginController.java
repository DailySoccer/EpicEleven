package controllers;

import actions.AllowCors;
import actions.UserAuthenticated;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;
import com.mongodb.MongoException;
import com.stormpath.sdk.account.Account;
import model.GlobalDate;
import model.Model;
import model.Session;
import model.User;
import model.stormpath.StormPathClient;
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

import static play.data.Form.form;

@AllowCors.Origin
public class LoginController extends Controller {

    // https://github.com/playframework/playframework/tree/master/samples/java/forms
    public static class SignupParams {

        public String firstName;

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

    public static class AskForPasswordResetParams {
        @Required public String email;
    }

    public static class VerifyPasswordResetTokenParams {
        @Required public String token;
    }

    public static class PasswordResetParams {
        @Required public String password;
        @Required public String token;
    }

    public static Result askForPasswordReset() {

        Form<AskForPasswordResetParams> askForPasswordResetParamsForm = form(AskForPasswordResetParams.class).bindFromRequest();
        AskForPasswordResetParams params = null;

        ReturnHelper returnHelper = new ReturnHelper();

        if (!askForPasswordResetParamsForm.hasErrors()) {
            params = askForPasswordResetParamsForm.get();

            String askForPasswordResetErrors = StormPathClient.instance().askForPasswordReset(params.email);

            if (askForPasswordResetErrors == null) {
                returnHelper.setOK(ImmutableMap.of("success", "Password reset sent"));
            }
            else {
                returnHelper.setKO(ImmutableMap.of("error", askForPasswordResetErrors));
            }
        }
        return returnHelper.toResult();
    }


    public static Result verifyPasswordResetToken() {
        Form<VerifyPasswordResetTokenParams> verifyPasswordResetTokenParamsForm = form(VerifyPasswordResetTokenParams.class).bindFromRequest();
        VerifyPasswordResetTokenParams params = null;

        ReturnHelper returnHelper = new ReturnHelper();

        if (!verifyPasswordResetTokenParamsForm.hasErrors()) {
            params = verifyPasswordResetTokenParamsForm.get();

            String verifyPasswordResetTokenErrors = StormPathClient.instance().verifyPasswordResetToken(params.token);

            if (verifyPasswordResetTokenErrors == null) {
                returnHelper.setOK(ImmutableMap.of("success", "Password reset token valid"));
            } else {
                returnHelper.setKO(ImmutableMap.of("error", verifyPasswordResetTokenErrors));
            }
        }
        return returnHelper.toResult();
    }


    public static Result resetPasswordWithToken() {

        Form<PasswordResetParams> passwordResetParamsForm = form(PasswordResetParams.class).bindFromRequest();
        PasswordResetParams params = null;

        ReturnHelper returnHelper = new ReturnHelper();

        if (!passwordResetParamsForm.hasErrors()) {
            params = passwordResetParamsForm.get();

            String resetPasswordWithTokenErrors = StormPathClient.instance().resetPasswordWithToken(params.token, params.password);

            if (resetPasswordWithTokenErrors == null) {
                returnHelper.setOK(ImmutableMap.of("success", "Password resetted successfully"));
            } else {
                returnHelper.setKO(ImmutableMap.of("error", resetPasswordWithTokenErrors));
            }
        }
        return returnHelper.toResult();
    }


    public static Result signup() {
        Form<SignupParams> signupForm = form(SignupParams.class).bindFromRequest();

        JsonNode result = signupForm.errorsAsJson();

        if (!signupForm.hasErrors()) {
            SignupParams params = signupForm.get();

            result = new ObjectMapper().createObjectNode().put("result", "ok");

            String createUserErrors = createUser(params);

            if (createUserErrors != null)
                signupForm.reject("error", createUserErrors);

        }

        return new ReturnHelper(!signupForm.hasErrors(), result).toResult();
    }


    private static String createUser(SignupParams theParams) {

        StormPathClient stormPathClient = new StormPathClient();
        String registerError = stormPathClient.register(theParams.nickName, theParams.email, theParams.password);

        if (registerError == null) {
            // Puede ocurrir que salte una excepcion por duplicidad. No seria un error de programacion puesto que, aunque
            // comprobamos si el email o nickname estan duplicados antes de llamar aqui, es posible que se creen en
            // paralelo. Por esto, la vamos a controlar explicitamente
            try {
                Model.users().insert(new User(theParams.firstName, theParams.lastName, theParams.nickName,
                                              theParams.email, theParams.password));
            } catch (MongoException exc) {
                Logger.error("createUser: ", exc);
                registerError = exc.toString();
            }

        }
        return registerError;
    }


    // TODO: Todo esto es totalmente incorrecto, es un borrador. Hay que hacer cosas como caducidad de sesiones,
    // confirmacion de la cuenta a traves de email, etc. Hay un monton de notas en Asana.
    public static Result login() {

        Form<LoginParams> loginParamsForm = Form.form(LoginParams.class).bindFromRequest();
        ReturnHelper returnHelper = new ReturnHelper();

        if (!loginParamsForm.hasErrors()) {
            LoginParams loginParams = loginParamsForm.get();

            boolean isTest = loginParams.email.endsWith("@test.com");

            // Si no es Test, entramos a través de Stormpath
            Account account = isTest? null : StormPathClient.instance().login(loginParams.email, loginParams.password);

            // Si no entra correctamente
            if (account == null && !isTest) {
                loginParamsForm.reject("email", "email or password incorrect");
                returnHelper.setKO(loginParamsForm.errorsAsJson());
            }
            // Si entramos correctamente
            else {
                // Buscamos el usuario en Mongo
                User theUser = Model.users().findOne("{email:'#'}", loginParams.email).as(User.class);

                // Si el usuario tiene cuenta en StormPath, pero no existe en nuestra BD, lo creamos en nuestra BD
                if (theUser == null && account != null) {
                    Logger.debug("Creamos el usuario porque no esta en nuestra DB y sí en Stormpath: {}", account.getEmail());
                    Model.users().insert(new User(account.getGivenName(), account.getSurname(),
                                                  account.getUsername(), account.getEmail(), ""));
                }

                if (Play.isDev()) {
                    Logger.info("Estamos en desarrollo: El email {} sera el sessionToken y pondremos una cookie", theUser.email);

                    // Durante el desarrollo en local usamos cookies y el email como sessionToken para que sea mas facil debugear
                    // por ejemplo usando Postman
                    response().setCookie("sessionToken", theUser.email);

                    returnHelper.setOK(new Session(theUser.email, theUser.userId, GlobalDate.getCurrentDate()));
                }
                else {
                    // En produccion NO mandamos cookie. Esto evita CSRFs. Esperamos que el cliente nos mande el sessionToken
                    // cada vez como parametro en una custom header.
                    String sessionToken = Crypto.generateSignedToken();
                    Session newSession = new Session(sessionToken, theUser.userId, GlobalDate.getCurrentDate());
                    Model.sessions().insert(newSession);

                    returnHelper.setOK(newSession);
                }
            }
        }

        return returnHelper.toResult();
    }

    @UserAuthenticated
    public static Result getUserProfile() {
        return new ReturnHelper(ctx().args.get("User")).toResult();
    }

    public static class ChangeParams {

        public String firstName;
        public String lastName;
        public String nickName;
        @Email public String email;

        public String password; //TODO: Cambiar la password no deberia ir en otro form aparte?
    }

    @UserAuthenticated
    public static Result changeUserProfile() {

        //TODO: ESTO NO ESTÁ SINCRONIZADO CON STORMPATH

        User theUser = (User)ctx().args.get("User");

        Form<ChangeParams> changeParamsForm = form(ChangeParams.class).bindFromRequest();
        ChangeParams params;

        if (!changeParamsForm.hasErrors()) {
            params = changeParamsForm.get();

            if (!params.firstName.isEmpty()) {
                theUser.firstName = params.firstName;
            }
            if (!params.lastName.isEmpty()) {
                theUser.lastName = params.lastName;
            }
            if (!params.nickName.isEmpty()) {
                User user = Model.users().findOne("{nickName:'#'}", params.nickName).as(User.class);
                if (user != null && !user.userId.equals(theUser.userId)) {
                    changeParamsForm.reject("nickName", "This nickName is already taken");
                }
                else {
                    theUser.nickName = params.nickName;
                }
            }
            if (!params.email.isEmpty()) {
                User user = Model.users().findOne("{email:'#'}", params.email).as(User.class);
                if (user != null && !user.email.equals(theUser.email)) {
                    changeParamsForm.reject("email", "This email is already taken");
                }
                else {
                    theUser.email = params.email;
                }
            }

            if (!changeParamsForm.hasErrors()) {
                Model.users().update(theUser.userId).with(theUser);
            }
        }

        JsonNode result = changeParamsForm.errorsAsJson();

        if (!changeParamsForm.hasErrors()) {
            result = new ObjectMapper().createObjectNode().put("result", "ok");
        }

        return new ReturnHelper(!changeParamsForm.hasErrors(), result).toResult();
    }


}