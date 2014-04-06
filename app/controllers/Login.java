package controllers;

import com.mongodb.MongoClient;
import com.mongodb.MongoClientURI;
import play.Logger;
import play.Play;
import play.data.validation.Constraints.*;
import play.mvc.Controller;
import play.mvc.Result;
import play.data.*;
import static play.data.Form.*;

public class Login extends Controller {

    // https://github.com/playframework/playframework/tree/master/samples/java/forms
    public static class SignupParams {
        @Required @MinLength(value = 4)
        public String first_name;

        @Required @MinLength(value = 4)
        public String last_name;

        @Required @Email public String email;

        public String password;
        public String repeat_password;
    }

    public static class LoginParams {
        @Required public String first_name;
        @Required public String password;
    }

    public static Result signup() {
        final Form<SignupParams> signupForm = form(SignupParams.class).bindFromRequest();

        if (!signupForm.hasErrors()) {
            if(signupForm.field("first_name").valueOr("").equals("admin"))
                signupForm.reject("first_name", "This username is already taken");

            if (!isSecurePassword(signupForm.field("password").valueOr("")))
                signupForm.reject("password", "Password is not secure");

            if (!signupForm.field("password").valueOr("").equals(signupForm.field("repeat_password").valueOr("")))
                signupForm.reject("repeat_password", "Passwords don't match");
        }

        if (!signupForm.hasErrors()) {
            SignupParams signupParams = signupForm.get();

            return ok(String.format("Hello %s", signupParams.first_name));
        }
        else {
            return badRequest(signupForm.errorsAsJson());
        }
    }

    public static Result login() {
        String mongodbUri = Play.application().configuration().getString("mongodb.uri");

        Logger.info("The MongoDB uri is {}", mongodbUri);

        try {
            MongoClient mongoClient = new MongoClient(new MongoClientURI(mongodbUri));
        } catch (Exception exc) {
            Logger.error("Error connecting to MongoDB {}", mongodbUri);
        }

        return ok("THIS_IS_THE_TOKEN");
    }

    public static Result user_profile() {
        return ok();
    }

    private static boolean isSecurePassword(String password) {
        return true;
    }
}
