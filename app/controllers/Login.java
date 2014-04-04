package controllers;

import play.data.validation.Constraints.*;
import play.mvc.Controller;
import play.mvc.Result;
import play.data.*;
import static play.data.Form.*;

public class Login extends Controller {

    // https://github.com/playframework/playframework/tree/master/samples/java/forms
    public static class SignupData {
        @Required @MinLength(value = 4)
        public String first_name;

        @Required @MinLength(value = 4)
        public String last_name;

        @Required @Email public String email;

        public String password;
        public String repeat_password;
    }

    public static Result signup() {
        final Form<SignupData> signupForm = form(SignupData.class).bindFromRequest();

        if (!signupForm.hasErrors()) {
            if(signupForm.field("first_name").valueOr("").equals("admin"))
                signupForm.reject("first_name", "This username is already taken");

            if (!isSecurePassword(signupForm.field("password").valueOr("")))
                signupForm.reject("password", "Password is not secure");

            if (!signupForm.field("password").valueOr("").equals(signupForm.field("repeat_password").valueOr("")))
                signupForm.reject("repeat_password", "Passwords don't match");
        }

        if (!signupForm.hasErrors()) {
            SignupData signupData = signupForm.get();

            return ok(String.format("Hello %s", signupData.first_name));
        }
        else {
            return badRequest(signupForm.errorsAsJson());
        }
    }

    private static boolean isSecurePassword(String password) {
        return true;
    }
}
