package controllers.admin;

import actions.CheckTargetEnvironment;
import actors.BotSystemActor;
import model.Model;
import model.Product;
import model.User;
import model.accounting.AccountOp;
import model.accounting.AccountingTran;
import model.opta.OptaCompetition;
import org.joda.money.CurrencyUnit;
import org.joda.money.Money;
import play.Logger;
import play.mvc.Controller;
import play.mvc.Result;
import utils.TargetEnvironment;

import java.util.ArrayList;
import java.util.List;

public class DashboardController extends Controller {
    public static Result index() {
        return ok(views.html.dashboard.render(OptaCompetition.findAllActive(), getBotsState()));
    }

    @CheckTargetEnvironment
    static public Result initialSetup() {
        PointsTranslationController.resetToDefault();
        TemplateContestController.createAll();
        return index();
    }

    @CheckTargetEnvironment
    static public Result resetDB() {
        Model.reset(false);
        FlashMessage.success("Reset DB: OK");
        return index();
    }


    static public Result getTargetEnvironment() {
        return ok(Model.getTargetEnvironment().toString());
    }

    static public Result startStopBotActors() {
        Model.actors().tellAndAwait("BotSystemActor", "StartStop");
        return redirect(routes.DashboardController.index());
    }

    static public Result startBotActors() {
        Model.actors().tellAndAwait("BotSystemActor", "Start");
        return redirect(routes.DashboardController.index());
    }

    static public Result stopBotActors() {
        Model.actors().tellAndAwait("BotSystemActor", "Stop");
        return redirect(routes.DashboardController.index());
    }

    static public Result pauseResumeBotActors() {
        Model.actors().tellAndAwait("BotSystemActor", "PauseResume");
        return redirect(routes.DashboardController.index());
    }

    static public Result stampedeBotActors() {
        Model.actors().tellAndAwait("BotSystemActor", "Stampede");
        return redirect(routes.DashboardController.index());
    }

    static public Result berserkerBotActors() {
        Model.actors().tellAndAwait("BotSystemActor", "Berserker");
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
            accountOps.add(new AccountOp(bot.userId, Money.of(Product.CURRENCY_DEFAULT, amount), bot.getSeqId() + 1));
        }

        if (!accountOps.isEmpty()) {
            AccountingTran accountingTran = new AccountingTran(AccountingTran.TransactionType.FREE_MONEY);
            accountingTran.accountOps = accountOps;
            accountingTran.insertAndCommit();
        }
    }

    static private BotSystemActor.ChildrenState getBotsState() {
        return Model.actors().tellAndAwait("BotSystemActor", "GetChildrenState");
    }
}