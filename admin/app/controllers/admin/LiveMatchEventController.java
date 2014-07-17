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
        Iterable<LiveMatchEvent> liveMatchEventResults = Model.liveMatchEvents().find().as(LiveMatchEvent.class);
        List<LiveMatchEvent> liveMatchEventList = ListUtils.asList(liveMatchEventResults);

        return ok(views.html.live_match_event_list.render(liveMatchEventList));
    }

    public static Result show(String liveMatchEventId) {
        ObjectId id = new ObjectId(liveMatchEventId);

        // Obtener la version actualizada
        LiveMatchEvent liveMatchEvent = LiveMatchEvent.findOne(id);
        return ok(views.html.live_match_event.render(liveMatchEvent));
    }

    public static Result showWithTemplate(String templateMatchEventId) {
        TemplateMatchEvent templateMatchEvent = TemplateMatchEvent.findOne(new ObjectId(templateMatchEventId));
        LiveMatchEvent liveMatchEvent = LiveMatchEvent.findFromTemplateMatchEvent(templateMatchEvent);
        return show(liveMatchEvent.liveMatchEventId.toString());
    }
}
