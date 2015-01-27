package controllers.admin;

import akka.actor.ActorRef;
import akka.actor.ActorSelection;
import akka.pattern.Patterns;
import akka.util.Timeout;
import model.MockData;
import model.Model;
import utils.TargetEnvironment;
import model.User;
import model.accounting.AccountOp;
import model.accounting.AccountingTran;
import model.opta.OptaCompetition;
import play.Logger;
import play.libs.Akka;
import play.mvc.Controller;
import play.mvc.Result;
import scala.concurrent.Await;
import scala.concurrent.Future;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class DashboardController extends Controller {
    public static Result index() {
        return ok(views.html.dashboard.render(OptaCompetition.findAllActive(), isBotActorsStarted()));
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
        Timeout timeout = new Timeout(scala.concurrent.duration.Duration.create(2000, TimeUnit.MILLISECONDS));
        ActorSelection actorRef = Akka.system().actorSelection("/user/BotParentActor");

        actorRef.tell(start ? "StartChildren" : "StopChildren", ActorRef.noSender());

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

    static private boolean isBotActorsStarted() {
        Timeout timeout = new Timeout(scala.concurrent.duration.Duration.create(500, TimeUnit.MILLISECONDS));
        ActorSelection actorRef = Akka.system().actorSelection("/user/BotParentActor");

        Future<Object> response = Patterns.ask(actorRef, "GetChildrenStarted", timeout);

        boolean bRet = false;

        try {
            bRet = (boolean)Await.result(response, timeout.duration());
        }
        catch(Exception e) {
            Logger.error("WTF 5120 isBotActorsStarted Timeout");
        }

        return bRet;
    }
}