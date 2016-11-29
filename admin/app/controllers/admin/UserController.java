package controllers.admin;

import com.google.common.collect.ImmutableList;
import model.Contest;
import model.GlobalDate;
import model.Model;
import model.User;
import model.accounting.AccountingTran;
import model.opta.OptaCompetition;
import org.bson.types.ObjectId;
import play.cache.Cached;
import play.mvc.Controller;
import play.mvc.Result;
import play.mvc.With;
import utils.ListUtils;
import utils.MoneyUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class UserController extends Controller {
    @Cached(key = "UserController.index", duration = 60 * 60)
    public static Result index() {
        List<User> userList = ListUtils.asList(Model.users()
                        .find()
                        .projection("{ nickName: 1, createdAt : 1, wins : 1, goldBalance : 1, earnedMoney : 1, trueSkill : 1 }")
                        .as(User.class));
        return ok(views.html.user_list.render(userList));
    }

    public static Result transactions(String userIdStr) {
        ObjectId userId = new ObjectId(userIdStr);
        User user = User.findOne(userId);
        List<AccountingTran> accountingTrans = AccountingTran.findAllFromUserId(userId);
        return ok(views.html.user_transactions.render(user, AccountingTran.findAllFromUserId(userId)));
    }

    public static Result indexAjax() {
        return PaginationData.withAjaxAndQuery(request().queryString(), Model.users(), null, User.class, new PaginationData() {
            public String projection() {
                return "{" +
                        "_id: 1, " +
                        "nickName: 1, " +
                        "createdAt: 1, " +
                        "wins: 1, " +
                        "trueSkill: 1," +
                        "earnedMoney : 1" +
                        "}";
            }

            public List<String> getFieldNames() {
                return ImmutableList.of(
                        "_id",
                        "nickName",
                        "createdAt",
                        "wins",
                        "",             // Gold Balance
                        "",             // Manager Balance
                        "",             // Energy Balance
                        "earnedMoney",
                        "trueSkill"
                );
            }

            public String getFieldByIndex(Object data, Integer index) {
                User user = (User) data;
                switch (index) {
                    case 0:
                        return user.userId.toString();
                    case 1:
                        return user.nickName;
                    case 2:
                        return GlobalDate.formatDate(user.createdAt);
                    case 3:
                        return String.valueOf(user.wins);
                    case 4:
                        return MoneyUtils.asString(user.calculateGoldBalance());
                    case 5:
                        return MoneyUtils.asString(user.earnedMoney);
                    case 6:
                        return String.valueOf(user.trueSkill);
                }
                return "<invalid value>";
            }

            public String getRenderFieldByIndex(Object data, String fieldValue, Integer index) {
                User user = (User) data;
                switch (index) {
                }
                return fieldValue;
            }
        });
    }
}
