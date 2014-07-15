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
        Iterable<ContestEntry> contestEntryResults = Model.contestEntries().find().as(ContestEntry.class);
        List<ContestEntry> contestEntryList = ListUtils.asList(contestEntryResults);

        return ok(views.html.live_contest_entry_list.render(contestEntryList));
    }

    public static Result show(String contestEntryId) {
        ContestEntry contestEntry = ContestEntry.findOne(new ObjectId(contestEntryId));
        List<SoccerPlayer> soccer_players = contestEntry.getSoccerPlayers();
        return ok(views.html.live_contest_entry.render(contestEntry, soccer_players));
    }
}
