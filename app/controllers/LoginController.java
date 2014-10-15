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

            Account account = StormPathClient.instance().askForPasswordReset(params.email);

            if (account != null) {
                returnHelper.setOK(ImmutableMap.of("success", "Password reset sent"));
            }
            else {
                returnHelper.setKO(ImmutableMap.of("error", "Something went wrong"));
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

            Account account = StormPathClient.instance().verifyPasswordResetToken(params.token);

            if (account != null) {
                returnHelper.setOK(ImmutableMap.of("success", "Password reset token valid"));
            } else {
                returnHelper.setKO(ImmutableMap.of("error", "Password reset token invalid"));
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

            Account account = StormPathClient.instance().resetPasswordWithToken(params.token, params.password);

            if (account != null) {
                returnHelper.setOK(ImmutableMap.of("success", "Password resetted successfully"));
            } else {
                returnHelper.setKO(ImmutableMap.of("error", "Something went wrong"));
            }
        }
        return returnHelper.toResult();
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

        StormPathClient stormPathClient = new StormPathClient();
        stormPathClient.register(theParams.nickName, theParams.email, theParams.password);

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

        Form<LoginParams> loginParamsForm = Form.form(LoginParams.class).bindFromRequest();
        ReturnHelper returnHelper = new ReturnHelper();

        if (!loginParamsForm.hasErrors()) {
            LoginParams loginParams = loginParamsForm.get();

            boolean isTest = loginParams.email.endsWith("@test.com");

            // Si no es Test, entramos a través de Stormpath
            Account loggedAccount = (!isTest)?
                                        StormPathClient.instance().login(loginParams.email, loginParams.password):
                                        null;

            // En todas partes tenemos usuarios Stormpath y usuarios Test.
            boolean correctLogin = (loggedAccount != null) || isTest;

            // Buscamos el usuario en Mongo
            // TODO: Necesitamos sanitizar el email
            User theUser = Model.users().findOne("{email:'#'}", loginParams.email).as(User.class);

            if (theUser == null || !isPasswordCorrect(theUser, loginParams.password)) {
                if (loggedAccount != null) {
                    Model.users().insert(new User("", "", loggedAccount.getUsername(),
                            loggedAccount.getEmail(), ""));
                }
            } else if (Play.isDev() && isTest) {
                correctLogin = true;
            }

            //Si no entra correctamente
            if (!correctLogin) {
                loginParamsForm.reject("email", "email or password incorrect");
                returnHelper.setKO(loginParamsForm.errorsAsJson());
            }
            // Si entramos correctamente
            else {
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
                    // TODO: Pensar la opcion: encriptar los datos necesarios para no tener que tener tabla de sesiones.
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

        public String password;
    }

    @UserAuthenticated
    public static Result changeUserProfile() {
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
            if (!params.password.isEmpty()) {
                theUser.password = params.password;
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

    private static boolean isPasswordCorrect(User theUser, String password) {
        //if (Play.isDev()) {
        //    return true;
        //}
        return null != StormPathClient.instance().login(theUser.email, password);
    }

    private static boolean isSecurePassword(String password) {
        return true;
    }

}