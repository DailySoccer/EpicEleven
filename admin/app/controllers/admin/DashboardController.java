package controllers.admin;

import actors.BotParentActor;
import model.MockData;
import model.Model;
import model.User;
import model.accounting.AccountOp;
import model.accounting.AccountingTran;
import model.opta.OptaCompetition;
import play.Logger;
import play.mvc.Controller;
import play.mvc.Result;
import utils.TargetEnvironment;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public class DashboardController extends Controller {
    public static Result index() {
        return ok(views.html.dashboard.render(OptaCompetition.findAllActive(), getBotsState()));
    }

    static public Result initialSetup() {
        PointsTranslationController.resetToDefault();
        TemplateContestController.createAll();
        return index();
    }

    static public Result resetDB() {
        Model.reset(false);
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

    static public Result startStopBotActors() {
        Model.getDailySoccerActors().tellToActorAwaitResult("BotParentActor", "StartStop");
        return redirect(routes.DashboardController.index());
    }

    static public Result pauseResumeBotActors() {
        Model.getDailySoccerActors().tellToActorAwaitResult("BotParentActor", "PauseResume");
        return redirect(routes.DashboardController.index());
    }

    static public Result stampedeBotActors() {
        Model.getDailySoccerActors().tellToActorAwaitResult("BotParentActor", "Stampede");
        return redirect(routes.DashboardController.index());
    }

    static public Result addMoneyToBots(Integer amount) {
        Logger.info("Ejecutando addMoneyToBots...");
        addMoney(User.findBots(), amount);
        Logger.info("addMoneyToBots OK");
        return ok("");
    }

    static public Result addMoneyToTests(Integer amount) {
        Logger.info("Ejecutando addMoneyToTests...");
        addMoney(User.findTests(), amount);
        Logger.info("addMoneyToTests OK");
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

    static private BotParentActor.ChildrenState getBotsState() {
        return Model.getDailySoccerActors().tellToActorAwaitResult("BotParentActor", "GetChildrenState");
    }
}