package controllers.admin;

import model.*;
import model.opta.OptaEvent;
import org.bson.types.ObjectId;
import play.mvc.Controller;
import play.mvc.Result;
import utils.ListUtils;

import java.util.ArrayList;
import java.util.List;

public class TemplateSoccerPlayerController extends Controller {
    public static Result index() {
        return ok(views.html.template_soccer_player_list.render(TemplateSoccerPlayer.findAll()));
    }

    public static Result showFantasyPointsInContest(String contestId, String playerId) {
        List<OptaEvent> optaEventList = new ArrayList<>();

        TemplateSoccerPlayer templateSoccerPlayer = TemplateSoccerPlayer.findOne(new ObjectId(playerId));
        Contest contest = Contest.findOne(new ObjectId(contestId));
        TemplateContest templateContest = TemplateContest.findOne(contest.templateContestId);
        List<TemplateMatchEvent> templateMatchEvents = templateContest.getTemplateMatchEvents();

        for (TemplateMatchEvent templateMatchEvent : templateMatchEvents) {
            optaEventList.addAll(OptaEvent.filter(templateMatchEvent.optaMatchEventId, templateSoccerPlayer.optaPlayerId));
        }

        return ok(views.html.player_fantasy_points.render(templateSoccerPlayer, optaEventList));
    }

    public static Result showFantasyPointsInMatchEvent(String templateMatchEventId, String playerId) {
        List<OptaEvent> optaEventList = new ArrayList<>();

        TemplateSoccerPlayer templateSoccerPlayer = TemplateSoccerPlayer.findOne(new ObjectId(playerId));
        TemplateMatchEvent templateMatchEvent = TemplateMatchEvent.findOne(new ObjectId(templateMatchEventId));

        optaEventList.addAll(OptaEvent.filter(templateMatchEvent.optaMatchEventId, templateSoccerPlayer.optaPlayerId));

        return ok(views.html.player_fantasy_points.render(templateSoccerPlayer, optaEventList));
    }
}
