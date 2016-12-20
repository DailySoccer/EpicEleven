package controllers.admin;

import com.google.common.collect.ImmutableList;
import model.Contest;
import model.GlobalDate;
import model.Model;
import model.User;
import model.accounting.AccountingTran;
import model.opta.OptaCompetition;
import org.bson.types.ObjectId;
import org.joda.time.DateTime;
import play.cache.Cached;
import play.mvc.Controller;
import play.mvc.Result;
import play.mvc.With;
import utils.ListUtils;
import utils.MoneyUtils;

import java.util.*;

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

    public static Result participation() {
        List<HashMap<String, String>> participation = new ArrayList<>();

        DateTime currentTime = new DateTime(GlobalDate.getCurrentDate());
        int dayOfWeek = currentTime.dayOfWeek().get();
        play.Logger.debug("DayOfWeek: {}", dayOfWeek );

        currentTime = currentTime.minusDays(dayOfWeek - 1);
        currentTime = currentTime.withTime(1, 0, 0, 0);

        Date startDate = currentTime.toDate();
        Date endDate = currentTime.plusDays(30).toDate();

        while (endDate.after(OptaCompetition.SEASON_DATE_START)) {

            // Buscamos los torneos entre las fechas deseadas
            List<Contest> contests = ListUtils.asList(Model.contests()
                    .find("{startDate: {$gte: #, $lt: #}}", startDate, endDate)
                    .projection("{ \"contestEntries.userId\": 1 }")
                    .as(Contest.class));

            int total = 0;

            // Contabilizamos los usuarios únicos
            Set<ObjectId> uniques = new HashSet<>();
            for (Contest contest : contests) {
                contest.contestEntries.forEach(contestEntry -> uniques.add(contestEntry.userId));
                total += contest.contestEntries.size();
            }

            // Averiguamos su TrueSkill
            List<User> users = ListUtils.asList(Model.users()
                    .find("{_id: {$in: #}}", uniques)
                    .projection("{ trueSkill : 1 }")
                    .as(User.class));

            int[] trueSkill = {0, 0, 0, 0, 0};
            for (User user : users) {
                int index = user.trueSkill / 1000;
                if (user.trueSkill < 1500) trueSkill[0]++;
                else if (user.trueSkill < 3000) trueSkill[1]++;
                else if (user.trueSkill < 4000) trueSkill[2]++;
                else if (user.trueSkill < 5000) trueSkill[3]++;
                else trueSkill[4]++;
            }

            // Registramos la estadística
            HashMap<String, String> stats = new HashMap<>();
            stats.put("startDate", GlobalDate.formatDate(startDate));
            stats.put("users", String.valueOf(uniques.size()));
            stats.put("total", String.valueOf(total));
            stats.put("NOVATO", String.valueOf(trueSkill[0]));
            stats.put("AMATEUR", String.valueOf(trueSkill[1]));
            stats.put("PROFESIONAL", String.valueOf(trueSkill[2]));
            stats.put("CRACK", String.valueOf(trueSkill[3]));
            stats.put("ESTRELLA", String.valueOf(trueSkill[4]));
            participation.add(stats);

            currentTime = currentTime.minusDays(7);
            endDate = startDate;
            startDate = currentTime.toDate();
        }


        return ok(views.html.users_participation.render(participation));
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
