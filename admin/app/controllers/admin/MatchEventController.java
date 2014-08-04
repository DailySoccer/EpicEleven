package controllers.admin;

import model.MatchEvent;
import org.bson.types.ObjectId;
import play.mvc.Controller;
import play.mvc.Result;

public class MatchEventController extends Controller {
    public static Result index() {
        return ok(views.html.match_event_list.render(MatchEvent.findAll()));
    }

    public static Result show(String matchEventId) {
        return ok(views.html.match_event.render(MatchEvent.findOne(new ObjectId(matchEventId))));
    }
}