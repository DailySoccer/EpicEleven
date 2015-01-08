package controllers;

import actions.AllowCors;
import actions.UserAuthenticated;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;
import com.mongodb.MongoException;
import com.stormpath.sdk.account.Account;
import com.stormpath.sdk.directory.CustomData;
import model.GlobalDate;
import model.Model;
import model.Session;
import model.User;
import stormpath.StormPathClient;
import play.Logger;
import play.Play;
import play.data.Form;
import play.data.validation.Constraints.Email;
import play.data.validation.Constraints.MinLength;
import play.data.validation.Constraints.Required;
import play.libs.Crypto;
import play.libs.F;
import play.mvc.Controller;
import play.mvc.Result;
import utils.ReturnHelper;

import java.util.HashMap;
import java.util.Map;

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

    public static class FBLoginParams {
        @Required public String accessToken;
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
        AskForPasswordResetParams params;

        ReturnHelper returnHelper = new ReturnHelper();

        if (!askForPasswordResetParamsForm.hasErrors()) {
            params = askForPasswordResetParamsForm.get();

            F.Tuple<Integer, String> askForPasswordResetErrors = StormPathClient.instance().askForPasswordReset(params.email);

            if (askForPasswordResetErrors._1 == -1) {
                returnHelper.setOK(ImmutableMap.of("success", "Instrucciones para cambiar contraseña enviadas."));
            }
            else {
                returnHelper.setKO(translateError(askForPasswordResetErrors));
            }
        }
        return returnHelper.toResult();
    }


    public static Result verifyPasswordResetToken() {
        Form<VerifyPasswordResetTokenParams> verifyPasswordResetTokenParamsForm = form(VerifyPasswordResetTokenParams.class).bindFromRequest();
        VerifyPasswordResetTokenParams params;

        ReturnHelper returnHelper = new ReturnHelper();

        if (!verifyPasswordResetTokenParamsForm.hasErrors()) {
            params = verifyPasswordResetTokenParamsForm.get();

            F.Tuple<Integer, String> verifyPasswordResetTokenErrors = StormPathClient.instance().verifyPasswordResetToken(params.token);

            if (verifyPasswordResetTokenErrors._1 == -1) {
                returnHelper.setOK(ImmutableMap.of("success", "Token valido"));
            } else {
                returnHelper.setKO(ImmutableMap.of("error", translateError(verifyPasswordResetTokenErrors)));
            }
        }
        return returnHelper.toResult();
    }


    public static Result resetPasswordWithToken() {

        Form<PasswordResetParams> passwordResetParamsForm = form(PasswordResetParams.class).bindFromRequest();
        PasswordResetParams params;

        ReturnHelper returnHelper = new ReturnHelper();

        if (StormPathClient.instance().isConnected() && !passwordResetParamsForm.hasErrors()) {
            params = passwordResetParamsForm.get();

            F.Tuple<Account, F.Tuple<Integer, String>> accountError = StormPathClient.instance().resetPasswordWithToken(params.token, params.password);

            User theUser = null;
            Account account = accountError._1;
            F.Tuple<Integer, String> error = accountError._2;
            if (account != null) {
                theUser = User.findByEmail(account.getEmail().toLowerCase());

                if (theUser == null) {
                    Logger.debug("Creamos el usuario porque no esta en nuestra DB y sí en Stormpath: {}", account.getEmail());

                    theUser = new User(account.getGivenName(), account.getSurname(), account.getGivenName(), account.getEmail().toLowerCase());
                    Model.users().insert(theUser);
                }
            }

            if (theUser != null) {
                setSession(returnHelper, theUser);
            } else {
                returnHelper.setKO(translateError(error));
            }

        }

        return returnHelper.toResult();
    }


    public static Result signup() {
        Form<SignupParams> signupForm = form(SignupParams.class).bindFromRequest();

        JsonNode result = signupForm.errorsAsJson();

        if (!signupForm.hasErrors()) {
            SignupParams params = signupForm.get();
            Map<String, String> createUserErrors = createUser(params);

            if (createUserErrors != null && !createUserErrors.isEmpty()) {
                for (String key : createUserErrors.keySet()) {
                    signupForm.reject(key, createUserErrors.get(key));
                }
                result = signupForm.errorsAsJson();
            }
            else {
                result = new ObjectMapper().createObjectNode().put("result", "ok");
            }
        }

        return new ReturnHelper(!signupForm.hasErrors(), result).toResult();
    }


    private static Map<String, String> createUser(SignupParams theParams) {

        F.Tuple<Integer, String> error = null;

        if (StormPathClient.instance().isConnected()) {
            error = StormPathClient.instance().register(theParams.nickName, theParams.email, theParams.password);
        }

        if (error == null || error._1 == -1) {
            // Puede ocurrir que salte una excepcion por duplicidad. No seria un error de programacion puesto que, aunque
            // comprobamos si el email o nickname estan duplicados antes de llamar aqui, es posible que se creen en
            // paralelo. Por esto, la vamos a controlar explicitamente

            // Puede estar ya el usuario si ha entrado con Facebook
            User theUser =  User.findByEmail(theParams.email.toLowerCase());

            if (theUser == null) {
                try {
                    Model.users().insert(new User(theParams.firstName, theParams.lastName, theParams.nickName,
                            theParams.email.toLowerCase()));
                } catch (MongoException exc) {
                    int mongoError = 0; //new F.Tuple<>(0, "Hubo un problema en la creación de tu usuario");
                    User user = model.User.findByName(theParams.nickName);
                    if (user != null) {
                        mongoError += 1;
                        //error = new F.Tuple<>(1, "Ya existe una cuenta con ese nombre de usuario. Elige uno diferente.");
                    }
                    user = model.User.findByEmail(theParams.email.toLowerCase());
                    if (user != null) {
                        mongoError += 2; //"Ya existe una cuenta con ese email. Indica otro email."
                    }
                    Logger.error("createUser: ", exc);
                    error = new F.Tuple<>(mongoError, "");
                }
            }

        }
        return error==null? new HashMap<String, String>(): translateError(error);
        //return translateError(registerError);
    }

    private static Map<String, String> translateError(F.Tuple<Integer, String> error) {
        HashMap<String, String> returnError = new HashMap<>();

        if (error._1 == 0) {
            returnError.put("email", "Hubo un problema en la creación de tu usuario");
        }
        else if (error._1 == 1) {
            returnError.put("nickName", "Ya existe una cuenta con ese nombre de usuario. Elige uno diferente.");
        }
        else if (error._1 == 2) {
            returnError.put("email", "Ya existe una cuenta con ese email. Indica otro email.");
        }
        else if (error._1 == 3) {
            returnError.put("nickName", "Ya existe una cuenta con ese nombre de usuario. Elige uno diferente.");
            returnError.put("email", "Ya existe una cuenta con ese email. Indica otro email.");
        }
        else if (error._1 == 2001) {
            if (error._2.contains("email")) {
                returnError.put("email", "Ya existe una cuenta con ese email. Indica otro email.");
            }
            else {
                returnError.put("nickName", "Ya existe una cuenta con ese nombre de usuario. Elige uno diferente.");
            }
        }
        else if (error._1 == 404) {
            returnError.put("email", "Algo ha ido mal, comprueba que la dirección esté bien escrita.");
        }
        else if (error._1 != -1) {
            returnError.put("error", (error._2));
        }

        return returnError;

        // TODO:
  /*
        if (error.contains("Account with that email already exists.  Please choose another email.")) {
            returnError.put("email", "Ya existe una cuenta con ese email. Indica otro email.");
        }
        else
        if (error.contains("Account with that username already exists.  Please choose another username.")) {
            returnError.put("nickName", "Ya existe una cuenta con ese nombre de usuario. Escoge otro.");
        }
        else
        if (error.contains("Minimum length is 4")) {
            returnError.put("nickName", "Al menos 4 caracteres");
        }
        else
        if (error.contains("Account password minimum length not satisfied.")) {
            returnError.put("password", "La contraseña es demasiado corta.");
        }
        else
        if (error.contains("Password requires a lowercase character!")) {
            returnError.put("password", "La contraseña debe contener al menos una letra minúscula");
        }
        else
        if (error.contains("Password requires an uppercase character!")) {
            returnError.put("password", "La contraseña debe contener al menos una letra mayúscula");
        }
        else
        if (error.contains("Password requires a numeric character!")) {
            returnError.put("password", "La contraseña debe contener al menos un número");
        }
  */

        //TODO: Incluir:
       // result.put("email", "Hubo un problema en la creación de tu usuario");

        /*
         Cannot invoke the action, eventually got an error: com.stormpath.sdk.resource.ResourceException: HTTP 409, Stormpath 409 (mailto:support@stormpath.com): Another resource with that information already exists. This is likely due to a constraint violation. Please update to retrieve the latest version of the information and try again if necessary.
         */
/*
        else {
            Logger.error("Error no traducido: \n {}", error);
        }
        return returnError;
*/
    }


    public static Result login() {

        Form<LoginParams> loginParamsForm = Form.form(LoginParams.class).bindFromRequest();
        ReturnHelper returnHelper = new ReturnHelper();

        if (!loginParamsForm.hasErrors()) {
            LoginParams loginParams = loginParamsForm.get();

            boolean isTest = loginParams.email.endsWith("@test.com") || !StormPathClient.instance().isConnected();

            // Si no es Test, entramos a través de Stormpath
            Account account = isTest? null : StormPathClient.instance().login(loginParams.email, loginParams.password);

            // Si entramos con Test, debemos entrar con correo, no con username
            String email = isTest? loginParams.email.toLowerCase(): account!=null? account.getEmail().toLowerCase(): null;

            if (email != null) {
                // Buscamos el usuario en Mongo
                User theUser = User.findByEmail(email);

                if (theUser == null) {
                    // Si el usuario tiene cuenta en StormPath, pero no existe en nuestra BD, lo creamos en nuestra BD
                    if (account != null) {
                        Logger.debug("Creamos el usuario porque no esta en nuestra DB y sí en Stormpath: {}", account.getEmail());

                        theUser = new User(account.getGivenName(), account.getSurname(), account.getUsername(), account.getEmail().toLowerCase());

                        Model.users().insert(theUser);
                    }
                    // Si el usuario no tiene cuenta en Stormpath ni lo encontramos en nuestra BD -> Reject
                    else {
                        loginParamsForm.reject("email", "Email o contraseña incorrectos");
                        returnHelper.setKO(loginParamsForm.errorsAsJson());
                    }
                }

                if (theUser != null) {
                    setSession(returnHelper, theUser);
                }
            }
            else {
                loginParamsForm.reject("email", "Email o contraseña incorrectos");
                returnHelper.setKO(loginParamsForm.errorsAsJson());
            }
        }

        return returnHelper.toResult();
    }


    public static Result facebookLogin() {

        Form<FBLoginParams> loginParamsForm = Form.form(FBLoginParams.class).bindFromRequest();
        ReturnHelper returnHelper = new ReturnHelper();

        if (!loginParamsForm.hasErrors()) {
            FBLoginParams loginParams = loginParamsForm.get();

            Account account = StormPathClient.instance().facebookLogin(loginParams.accessToken);
            User theUser;
            if (account != null) {
                theUser = User.findByEmail(account.getEmail().toLowerCase());

                if (theUser == null) {
                    Logger.debug("Creamos el usuario porque no esta en nuestra DB y sí en Stormpath: {}", account.getEmail());

                    theUser = new User(account.getGivenName(), account.getSurname(), getOrSetNickname(account), account.getEmail().toLowerCase());
                    Model.users().insert(theUser);
                }

                setSession(returnHelper, theUser);

            }
            else {
                loginParamsForm.reject("email", "Token incorrecto");
                returnHelper.setKO(loginParamsForm.errorsAsJson());
            }
        }
        return returnHelper.toResult();
    }

    private static String getOrSetNickname(Account account) {
        // Si el nickname está en Stormpath ,lo cogemos de ahí.
        // Si no, lo generamos y lo guardamos en stormpath
        // Devolvemos el nickname
        String nickname;
        CustomData customData = StormPathClient.instance().getCustomDataForAccount(account);
        if (customData.containsKey("nickname")) {
            nickname = (String) customData.get("nickname");
        }
        else {
            nickname = generateNewNickname(account);
            customData.put("nickname", nickname);
            customData.save();
        }
        return nickname;
    }

    private static String generateNewNickname(Account account) {
        String base = account.getGivenName()+" "+account.getSurname();
        String nickname = base;
        int count = 0;

        while (null != User.findByName(nickname)) {
            nickname = base+" "+Integer.toString(++count);
        }

        return nickname;
    }

    private static void setSession(ReturnHelper returnHelper, User theUser) {

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
            Session session = Model.sessions().findOne("{userId: #}", theUser.userId).as(Session.class);

            if (session == null) {
                String sessionToken = Crypto.generateSignedToken();
                session = new Session(sessionToken, theUser.userId, GlobalDate.getCurrentDate());
                Model.sessions().insert(session);
            }

            returnHelper.setOK(session);
        }
    }

    @UserAuthenticated
    public static Result getUserProfile() {
        return new ReturnHelper(((User)ctx().args.get("User")).getProfile()).toResult();
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
        Object result = theUser;
        Map<String, String> allErrors = new HashMap<>();

        boolean somethingChanged = false;

        if (!changeParamsForm.hasErrors()) {
            params = changeParamsForm.get();
            String originalEmail = theUser.email;

            if (!params.firstName.isEmpty()) {
                theUser.firstName = params.firstName;
                somethingChanged = true;
            }
            if (!params.lastName.isEmpty()) {
                theUser.lastName = params.lastName;
                somethingChanged = true;
            }
            if (!params.nickName.isEmpty()) {
                theUser.nickName = params.nickName;
                somethingChanged = true;
            }
            if (!params.email.isEmpty()) {
                theUser.email = params.email.toLowerCase();
                somethingChanged = true;
            }
            if (!params.password.isEmpty()) {
                somethingChanged = true;
            }


            if (somethingChanged) {
                Map<Integer, String> stormpathErrors = changeStormpathProfile(theUser, params, somethingChanged, originalEmail);

                if (!stormpathErrors.isEmpty()) {
                    if (stormpathErrors.containsKey(409)) {
                        if (!params.nickName.isEmpty() && null != User.findByName(params.nickName)) {
                            allErrors.put("nickName", "Ya existe una cuenta con ese nombre de usuario. Elige uno diferente.");
                        }

                        if (!params.email.isEmpty() && null != User.findByEmail(params.email)) {
                            allErrors.put("email", "Ya existe una cuenta con ese email. Indica otro email.");
                        }
                    }
                    if (stormpathErrors.containsKey(2007)) {
                        allErrors.put("password", "La contraseña es demasiado corta.");
                    }

                }

                if (allErrors.isEmpty()) {
                    Model.users().update(theUser.userId).with(theUser);
                }
                else {
                    for (String key : allErrors.keySet()) {
                        changeParamsForm.reject(key, allErrors.get(key));
                    }
                    result = changeParamsForm.errorsAsJson();
                }
            }

        }
        else {
            result = changeParamsForm.errorsAsJson();
        }

        return new ReturnHelper(!changeParamsForm.hasErrors(), result).toResult();
    }


    private static Map<Integer, String> changeStormpathProfile(User theUser, ChangeParams params, boolean somethingChanged, String originalEmail) {

        StormPathClient stormPathClient = StormPathClient.instance();
        Map<Integer, String> allErrors = new HashMap<>();

        if (!originalEmail.endsWith("test.com")) {
            if (somethingChanged) {
                F.Tuple<Integer, String> profErrors = stormPathClient.changeUserProfile(originalEmail, theUser.firstName, theUser.lastName, theUser.nickName, theUser.email);
                allErrors.put(profErrors._1, profErrors._2);
            }

            if (params.password.length() > 0) {
                F.Tuple<Integer, String> upErrors = stormPathClient.updatePassword(originalEmail, params.password);
                allErrors.put(upErrors._1, upErrors._2);
            }
        }
        return allErrors;
    }


}