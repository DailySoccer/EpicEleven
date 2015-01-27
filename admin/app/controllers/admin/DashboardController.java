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
        F.Tuple<Boolean, String> status = getBotsStatus();

        return ok(views.html.dashboard.render(OptaCompetition.findAllActive(), status._1, status._2));
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
        List<AccountOp> accountOps = new ArrayList<>();
        for (User bot : User.findBots()) {
            accountOps.add(new AccountOp(bot.userId, new BigDecimal(amount), bot.getSeqId() + 1));
        }

        if (!accountOps.isEmpty()) {
            AccountingTran accountingTran = new AccountingTran(AccountingTran.TransactionType.MONEY);
            accountingTran.accountOps = accountOps;
            accountingTran.insertAndCommit();
        }
        return ok("");
    }

    static private F.Tuple<Boolean, String> getBotsStatus() {
        Object ret = Model.getDailySoccerActors().tellToActorAwaitResult("BotParentActor", "GetChildrenStarted");

        if (ret == null) {
            return new F.Tuple<>(false, "Unknown (TODO RPC)");
        }
        else {
            boolean isStarted = (boolean)ret;
            return new F.Tuple<>(isStarted, isStarted? "Stop Actors" : "Start Actors");
        }
    }
}