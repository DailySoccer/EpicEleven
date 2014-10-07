package controllers.admin;

import model.*;
import model.opta.OptaEvent;
import org.bson.types.ObjectId;
import play.mvc.Controller;
import play.mvc.Result;
import utils.ListUtils;

import java.util.HashMap;
import java.util.List;

public class TemplateMatchEventController extends Controller {
    public static Result index() {
        return ok(views.html.template_match_event_list.render(TemplateMatchEvent.findAll(), TemplateSoccerTeam.findAllAsMap()));
    }

    public static Result show(String templateMatchEventId) {
        return ok(views.html.template_match_event.render(TemplateMatchEvent.findOne(new ObjectId(templateMatchEventId)), TemplateSoccerTeam.findAllAsMap()));
    }

    public static Result showOptaEvents(String matchEventId) {
        TemplateMatchEvent matchEvent = TemplateMatchEvent.findOne(new ObjectId(matchEventId));

        Iterable<OptaEvent> optaEventResults = Model.optaEvents().find("{gameId: #, points: { $ne: 0 }}", matchEvent.optaMatchEventId).as(OptaEvent.class);
        List<OptaEvent> optaEventList = ListUtils.asList(optaEventResults);

        return ok(views.html.match_event_opta_events_list.render(optaEventList, getPlayersInfo(matchEvent)));
    }

    private static HashMap<String, String> getPlayersInfo(TemplateMatchEvent matchEvent){
        HashMap<String, String> map = new HashMap<>();

        TemplateSoccerTeam templateSoccerTeamA = TemplateSoccerTeam.findOne(matchEvent.templateSoccerTeamAId);
        for (TemplateSoccerPlayer soccerPlayer : TemplateSoccerPlayer.findAllFromTemplateTeam(templateSoccerTeamA.templateSoccerTeamId)) {
            map.put(soccerPlayer.optaPlayerId, soccerPlayer.name);
            map.put(soccerPlayer.optaPlayerId.concat("-team"), templateSoccerTeamA.name);
        }

        TemplateSoccerTeam templateSoccerTeamB = TemplateSoccerTeam.findOne(matchEvent.templateSoccerTeamBId);
        for (TemplateSoccerPlayer soccerPlayer : TemplateSoccerPlayer.findAllFromTemplateTeam(templateSoccerTeamB.templateSoccerTeamId)) {
            map.put(soccerPlayer.optaPlayerId, soccerPlayer.name);
            map.put(soccerPlayer.optaPlayerId + "-team", templateSoccerTeamB.name);
        }
        return map;
    }

}
