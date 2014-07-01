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
        Iterable<TemplateSoccerPlayer> soccerPlayerResults = Model.templateSoccerPlayers().find().as(TemplateSoccerPlayer.class);
        List<TemplateSoccerPlayer> soccerPlayerList = ListUtils.asList(soccerPlayerResults);

        return ok(views.html.template_soccer_player_list.render(soccerPlayerList));
    }

    public static Result showFantasyPointsInContest(String contestId, String playerId) {
        List<OptaEvent> optaEventList = new ArrayList<>();

        TemplateSoccerPlayer templateSoccerPlayer = TemplateSoccerPlayer.find(new ObjectId(playerId));
        Contest contest = Contest.find(new ObjectId(contestId));
        TemplateContest templateContest = TemplateContest.find(contest.templateContestId);
        List<TemplateMatchEvent> templateMatchEvents = templateContest.templateMatchEvents();

        for (TemplateMatchEvent templateMatchEvent : templateMatchEvents) {
            optaEventList.addAll(OptaEvent.filter(templateMatchEvent.optaMatchEventId, templateSoccerPlayer.optaPlayerId));
        }

        return ok(views.html.player_fantasy_points.render(templateSoccerPlayer, optaEventList));
    }

    public static Result showFantasyPointsInMatchEvent(String templateMatchEventId, String playerId) {
        List<OptaEvent> optaEventList = new ArrayList<>();

        TemplateSoccerPlayer templateSoccerPlayer = TemplateSoccerPlayer.find(new ObjectId(playerId));
        TemplateMatchEvent templateMatchEvent = TemplateMatchEvent.find(new ObjectId(templateMatchEventId));

        optaEventList.addAll(OptaEvent.filter(templateMatchEvent.optaMatchEventId, templateSoccerPlayer.optaPlayerId));

        return ok(views.html.player_fantasy_points.render(templateSoccerPlayer, optaEventList));
    }
}
