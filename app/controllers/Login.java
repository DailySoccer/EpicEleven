package controllers;

import com.mongodb.DB;
import model.Model;
import model.User;
import org.jongo.Jongo;
import org.jongo.MongoCollection;
import play.data.validation.Constraints.*;
import play.mvc.Controller;
import play.mvc.Result;
import play.data.*;
import static play.data.Form.*;
import play.Logger;

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

        public String password;
        public String repeatPassword;
    }

    public static class LoginParams {
        @Required public String email;
        @Required public String password;
    }

    public static Result signup() {
        final Form<SignupParams> signupForm = form(SignupParams.class).bindFromRequest();

        if (!signupForm.hasErrors()) {
            if(signupForm.field("nickName").valueOr("").equals("admin"))
                signupForm.reject("nickName", "This nickName is already taken");

            if (!isSecurePassword(signupForm.field("password").valueOr("")))
                signupForm.reject("password", "Password is not secure");

            if (!signupForm.field("password").valueOr("").equals(signupForm.field("repeatPassword").valueOr("")))
                signupForm.reject("repeatPassword", "Passwords don't match");
        }

        if (!signupForm.hasErrors()) {
            // TODO: Exceptions managing
            createUser(signupForm.get());
            return ok();
        }
        else {
            return badRequest(signupForm.errorsAsJson());
        }
    }

    private static void createUser(SignupParams theParams) {
        final Jongo jongo = Model.createJongo();

        MongoCollection users = jongo.getCollection("users");
        users.insert(new User(theParams.firstName, theParams.lastName, theParams.nickName,
                              theParams.email, theParams.password));
    }

    public static Result login() {


        return ok("THIS_IS_THE_TOKEN");
    }

    public static Result user_profile() {
        return ok();
    }

    private static boolean isSecurePassword(String password) {
        return true;
    }
}