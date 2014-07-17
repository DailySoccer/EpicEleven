package controllers.admin;

import model.ContestEntry;
import model.Model;
import model.SoccerPlayer;
import org.bson.types.ObjectId;
import play.mvc.Controller;
import play.mvc.Result;
import utils.ListUtils;

import java.util.List;

public class LiveContestEntryController extends Controller {
    public static Result index() {
        return ok(views.html.live_contest_entry_list.render(ContestEntry.findAllFromContests()));
    }

    public static Result show(String contestEntryId) {
        ContestEntry contestEntry = ContestEntry.findOne(new ObjectId(contestEntryId));
        return ok(views.html.live_contest_entry.render(
                contestEntry,
                contestEntry.getSoccerPlayers()));
    }
}
