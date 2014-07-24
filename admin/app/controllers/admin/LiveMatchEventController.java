package controllers.admin;

import model.LiveMatchEvent;
import model.Model;
import model.TemplateMatchEvent;
import org.bson.types.ObjectId;
import play.mvc.Controller;
import play.mvc.Result;
import utils.ListUtils;

import java.util.List;

public class LiveMatchEventController extends Controller {
    public static Result index() {
        List<LiveMatchEvent> liveMatchEventList = null;
        return ok(views.html.live_match_event_list.render(liveMatchEventList));
    }

    public static Result show(String liveMatchEventId) {
        LiveMatchEvent liveMatchEvent = null;
        return ok(views.html.live_match_event.render(liveMatchEvent));
    }

    public static Result showWithTemplate(String templateMatchEventId) {
        LiveMatchEvent liveMatchEvent = null;
        return show(liveMatchEvent.liveMatchEventId.toString());
    }
}
