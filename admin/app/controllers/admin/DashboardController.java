package controllers.admin;

import akka.actor.ActorSelection;
import akka.pattern.Patterns;
import akka.util.Timeout;
import model.MockData;
import model.Model;
import model.opta.OptaCompetition;
import play.Logger;
import play.libs.Akka;
import play.mvc.Controller;
import play.mvc.Result;
import scala.concurrent.Await;
import scala.concurrent.Future;

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

        Model.resetDB();
        MockData.ensureMockDataUsers();
        MockData.ensureCompetitions();

        FlashMessage.success("Reset DB: OK");

        return index();
    }

    static public Result setMongoAppEnv(String app) {
        Model.ensureMongo(app);
        return ok("");
    }

    static public Result getMongoAppEnv() {
        return ok(Model.getMongoAppEnv());
    }

    static public Result switchBotActors(boolean start) {
        Timeout timeout = new Timeout(scala.concurrent.duration.Duration.create(500, TimeUnit.MILLISECONDS));
        ActorSelection actorRef = Akka.system().actorSelection("/user/BotParentActor");

        Future<Object> response = Patterns.ask(actorRef, start? "StartChildren" : "StopChildren", timeout);

        try {
            Await.result(response, timeout.duration());
        }
        catch(Exception e) {
            Logger.error("WTF 5120 switchBotActors Timeout");
        }

        return redirect(routes.DashboardController.index());
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