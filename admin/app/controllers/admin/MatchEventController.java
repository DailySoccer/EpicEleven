package controllers.admin;

import model.MatchEvent;
import model.Model;
import model.SoccerPlayer;
import model.SoccerTeam;
import model.opta.OptaEvent;
import org.bson.types.ObjectId;
import play.mvc.Controller;
import play.mvc.Result;
import utils.ListUtils;

import java.util.ArrayList;
import java.util.List;

public class MatchEventController extends Controller {
    public static Result index() {
        return ok(views.html.match_event_list.render(MatchEvent.findAll()));
    }

    public static Result show(String matchEventId) {
        return ok(views.html.match_event.render(MatchEvent.findOne(new ObjectId(matchEventId))));
    }

    public static Result showOptaEvents(String matchEventId) {
        MatchEvent matchEvent = MatchEvent.findOne(new ObjectId(matchEventId));

        Iterable<OptaEvent> optaEventResults = Model.optaEvents().find("{gameId: #, points: { $ne: 0 }}", matchEvent.optaMatchEventId).as(OptaEvent.class);
        List<OptaEvent> optaEventList = ListUtils.asList(optaEventResults);

        return ok(views.html.match_event_opta_events_list.render(optaEventList, matchEvent.getSoccerTeamsAsMap(), matchEvent.getSoccerPlayersAsMap()));
    }
}