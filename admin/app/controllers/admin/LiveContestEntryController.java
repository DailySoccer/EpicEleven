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
    public static Result liveContestEntry(String contestEntryId) {
        ContestEntry contestEntry = ContestEntry.find(new ObjectId(contestEntryId));
        List<SoccerPlayer> soccer_players = ContestEntry.getSoccerPlayers(contestEntryId);
        return ok(views.html.live_contest_entry.render(contestEntry, soccer_players));
    }

    public static Result index() {
        Iterable<ContestEntry> contestEntryResults = Model.contestEntries().find().as(ContestEntry.class);
        List<ContestEntry> contestEntryList = ListUtils.asList(contestEntryResults);

        return ok(views.html.live_contest_entry_list.render(contestEntryList));
    }
}
