package controllers.admin;

import model.Model;
import model.User;
import play.mvc.Controller;
import play.mvc.Result;
import utils.ListUtils;

import java.util.List;

public class UserController extends Controller {
    public static Result index() {
        List<User> userList = ListUtils.asList(Model.users().find().as(User.class));

        return ok(views.html.user_list.render(userList));
    }
}
