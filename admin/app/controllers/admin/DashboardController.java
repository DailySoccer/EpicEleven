package controllers.admin;

import actions.CheckTargetEnvironment;
import actors.BotSystemActor;
import org.bson.types.ObjectId;
import model.Model;
import model.User;
import model.accounting.AccountOp;
import model.accounting.AccountingTran;
import model.accounting.AccountingTranBonus;
import model.opta.OptaCompetition;
import org.joda.money.CurrencyUnit;
import org.joda.money.Money;
import play.Logger;
import play.mvc.Controller;
import play.mvc.Result;
import utils.MoneyUtils;

import java.util.List;
import java.util.stream.Collectors;

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

    static public Result addMoneyToBots(String amount) {
        Logger.info("Ejecutando addMoneyToBots...");
        addMoney(User.findBots(), amount);
        Logger.info("addMoneyToBots OK");
        return ok("");
    }

    static public Result addMoneyToTests(String amount) {
        Logger.info("Ejecutando addMoneyToTests... {}", amount);
        addMoney(User.findTests(), amount);
        Logger.info("addMoneyToTests OK");
        return ok("");
    }

    static private void addMoney(List<User> users, String amount) {
        Money money = Money.parse(amount);

        if (!users.isEmpty()) {
            if (money.getCurrencyUnit().equals(MoneyUtils.CURRENCY_GOLD)) {
                List<AccountOp> accountOps = users.stream()
                        .map((user) -> new AccountOp(user.userId, money, user.getSeqId() + 1))
                        .collect(Collectors.toList());

                AccountingTran accountingTran = new AccountingTran(money.getCurrencyUnit().getCode(), AccountingTran.TransactionType.FREE_MONEY);
                accountingTran.accountOps = accountOps;
                accountingTran.insertAndCommit();
            }
            else if (money.getCurrencyUnit().equals(MoneyUtils.CURRENCY_MANAGER)) {
                users.forEach( user -> user.addManagerBalance(money) );
            }
            else if (money.getCurrencyUnit().equals(MoneyUtils.CURRENCY_ENERGY)) {
                users.forEach( user -> user.addEnergy(money) );
            }
        }
    }

    static private BotSystemActor.ChildrenState getBotsState() {
        return Model.actors().tellAndAwait("BotSystemActor", "GetChildrenState");
    }
}