package controllers.admin;

import model.Model;
import play.mvc.Controller;
import play.mvc.Result;

import java.util.HashMap;
import java.util.Map;

public class MigrationsController extends Controller {
    enum MigrationType {
        CONTEST_FREE_SLOTS
    };

    public static Result index() {
        return ok(views.html.migrations.render(evaluate()));
    }

    private static Map<String, String> evaluate() {
        Map<String, String> ret = new HashMap<>();

        long withoutFreeSlots = Model.contests().count("{freeSlots: {$exists: false}}");
        if (withoutFreeSlots > 0) {
            ret.put(MigrationType.CONTEST_FREE_SLOTS.name(), String.format("%d", withoutFreeSlots));
        }

        return ret;
    }

    public static Result apply(String type) {
        MigrationType migrationType = MigrationType.valueOf(type);

        switch (migrationType) {
            case CONTEST_FREE_SLOTS:
                String js = "function() {" +
                        "  db.contests.find({freeSlots : {$exists: false}}).forEach(function (contest) {" +
                        "     contest.freeSlots = NumberInt(contest.maxEntries - contest.contestEntries.length);" +
                        "     db.contests.save(contest);" +
                        "  })" +
                        "}";
                Model.runCommand("{ eval: # }", js);
                break;
        }

        return redirect(routes.MigrationsController.index());
    }
}
