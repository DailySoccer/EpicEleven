package controllers.admin;

import model.MockData;
import model.Model;
import model.User;
import model.accounting.AccountOp;
import model.accounting.AccountingTran;
import model.opta.OptaCompetition;
import play.libs.F;
import play.mvc.Controller;
import play.mvc.Result;
import utils.TargetEnvironment;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public class DashboardController extends Controller {
    public static Result index() {
        return ok(views.html.dashboard.render(OptaCompetition.findAllActive(), getBotActorsStarted()));
    }

    static public Result initialSetup() {
        PointsTranslationController.resetToDefault();
        TemplateContestController.createAll();
        return index();
    }

    static public Result resetDB() {

        Model.resetMongoDB();
        MockData.ensureMockDataUsers();
        MockData.ensureCompetitions();

        FlashMessage.success("Reset DB: OK");

        return index();
    }

    static public Result setTargetEnvironment(String env) {
        Model.setTargetEnvironment(TargetEnvironment.valueOf(env));
        return ok("");
    }

    static public Result getTargetEnvironment() {
        return ok(Model.getTargetEnvironment().toString());
    }

    static public Result switchBotActors(boolean start) {

        Model.getDailySoccerActors().tellToActor("BotParentActor", start ? "StartChildren" : "StopChildren");

        return redirect(routes.DashboardController.index());
    }

    static public Result addMoneyToBots(Integer amount) {
        addMoney(User.findBots(), amount);
        return ok("");
    }

    static public Result addMoneyToTests(Integer amount) {
        addMoney(User.findTests(), amount);
        return ok("");
    }

    static private void addMoney(List<User> users, Integer amount) {
        List<AccountOp> accountOps = new ArrayList<>();
        for (User bot : users) {
            accountOps.add(new AccountOp(bot.userId, new BigDecimal(amount), bot.getSeqId() + 1));
        }

        if (!accountOps.isEmpty()) {
            AccountingTran accountingTran = new AccountingTran(AccountingTran.TransactionType.FREE_MONEY);
            accountingTran.accountOps = accountOps;
            accountingTran.insertAndCommit();
        }
    }

    static private boolean getBotActorsStarted() {
        return (Boolean)Model.getDailySoccerActors().tellToActorAwaitResult("BotParentActor", "GetChildrenStarted");
    }
}