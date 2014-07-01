package controllers.admin;

import model.Model;
import model.User;
import play.mvc.Controller;
import play.mvc.Result;
import utils.ListUtils;

import java.util.List;

public class UserController extends Controller {
    public static Result index() {
        Iterable<User> userResults = Model.users().find().as(User.class);
        List<User> userList = ListUtils.asList(userResults);

        return ok(views.html.user_list.render(userList));
    }
}
