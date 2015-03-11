package utils;

import model.Model;
import play.Logger;
import play.mvc.Result;

import java.util.HashMap;
import java.util.Map;

public class Migrations {
    enum MigrationType {
        CONTEST_FREE_SLOTS
    };

    public static Map<String, String> evaluate() {
        Map<String, String> ret = new HashMap<>();

        // Contest free Slots?
        long withoutFreeSlots = Model.contests().count("{freeSlots: {$exists: false}}");
        if (withoutFreeSlots > 0) {
            ret.put(MigrationType.CONTEST_FREE_SLOTS.name(), String.format("%d", withoutFreeSlots));
        }

        return ret;
    }

    public static void applyAll() {
        evaluate().keySet().forEach(Migrations::apply);
    }

    public static void apply(String type) {
        switch (MigrationType.valueOf(type)) {
            case CONTEST_FREE_SLOTS:
                String js = "function() {" +
                        "  db.contests.find({freeSlots : {$exists: false}}).forEach(function (contest) {" +
                        "     contest.freeSlots = NumberInt(contest.maxEntries - contest.contestEntries.length);" +
                        "     db.contests.save(contest);" +
                        "  })" +
                        "}";
                Model.runCommand("{ eval: # }", js);
                break;

            default:
                Logger.error("WTF 1999: Migration not apply: {}", type);
        }
    }
}
