package controllers.admin;

import org.bson.types.ObjectId;
import model.Model;
import model.User;
import model.accounting.AccountingOp;
import play.mvc.Controller;
import play.mvc.Result;
import utils.ListUtils;
import java.util.List;

public class UserController extends Controller {
    public static Result index() {
        List<User> userList = ListUtils.asList(Model.users().find().as(User.class));

        return ok(views.html.user_list.render(userList));
    }

    public static Result transactions(String userIdStr) {
        ObjectId userId = new ObjectId(userIdStr);
        User user = User.findOne(userId);
        List<AccountingOp> accountingOps = AccountingOp.findAllFromUserId(userId);
        return ok(views.html.user_transactions.render(user, accountingOps));
    }
}
