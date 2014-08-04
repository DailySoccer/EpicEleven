package controllers.admin;

import model.Model;
import model.TemplateMatchEvent;
import model.TemplateSoccerTeam;
import org.bson.types.ObjectId;
import play.mvc.Controller;
import play.mvc.Result;
import utils.ListUtils;

import java.util.List;

public class TemplateMatchEventController extends Controller {
    public static Result index() {
        return ok(views.html.template_match_event_list.render(TemplateMatchEvent.findAll(), TemplateSoccerTeam.findAllAsMap()));
    }

    public static Result show(String templateMatchEventId) {
        return ok(views.html.template_match_event.render(TemplateMatchEvent.findOne(new ObjectId(templateMatchEventId)), TemplateSoccerTeam.findAllAsMap()));
    }
}
