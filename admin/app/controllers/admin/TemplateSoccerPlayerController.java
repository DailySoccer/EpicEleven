package controllers.admin;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import model.*;
import model.opta.OptaEvent;
import org.bson.types.ObjectId;
import play.mvc.Controller;
import play.mvc.Result;
import utils.PaginationData;

import java.util.ArrayList;
import java.util.List;

public class TemplateSoccerPlayerController extends Controller {
    public static Result index() {
        return ok(views.html.template_soccer_player_list.render(false));
    }

    public static Result editSalaries() {
        return ok(views.html.template_soccer_player_list.render(true));
    }

    public static Result indexAjax() {
        return PaginationData.withAjax(request().queryString(), Model.templateSoccerPlayers(), TemplateSoccerPlayer.class, new PaginationData() {
            public List<String> getFieldNames() {
                return ImmutableList.of(
                        "activated",
                        "optaPlayerId",
                        "name",
                        "fieldPos",
                        "salary",
                        "fantasyPoints",
                        "",
                        ""                  // Team
                );
            }

            public String getFieldByIndex(Object data, Integer index) {
                TemplateSoccerPlayer templateSoccerPlayer = (TemplateSoccerPlayer) data;
                switch (index) {
                    case 0: return String.valueOf(templateSoccerPlayer.activated);
                    case 1: return templateSoccerPlayer.optaPlayerId;
                    case 2: return templateSoccerPlayer.name;
                    case 3: return templateSoccerPlayer.fieldPos.toString();
                    case 4: return String.valueOf(templateSoccerPlayer.salary);
                    case 5: return String.valueOf(templateSoccerPlayer.fantasyPoints);
                    case 6: return String.valueOf(templateSoccerPlayer.getPlayedMatches());
                    case 7:
                            TemplateSoccerTeam templateSoccerTeam = TemplateSoccerTeam.findOne(templateSoccerPlayer.templateTeamId);
                            return templateSoccerTeam.name;
                }
                return "<invalid value>";
            }

            public String getRenderFieldByIndex(Object data, String fieldValue, Integer index) {
                TemplateSoccerPlayer templateSoccerPlayer = (TemplateSoccerPlayer) data;
                switch (index) {
                    case 0:
                        return String.format("<input class=\"edit-activated\" type=\"checkbox\" %s data-competition-id=\"%s\" value=\"%s\">",
                                templateSoccerPlayer.activated ? "checked" : "",
                                templateSoccerPlayer.templateSoccerPlayerId,
                                fieldValue);
                    case 4:
                        return String.format("<p class=\"edit-salary\" contenteditable=\"false\" data-player-id=%s>%s</p>",
                                    templateSoccerPlayer.templateSoccerPlayerId,
                                    fieldValue);
                    case 5:
                        return String.format("<a href=\"%s\" tabIndex=\"-1\">%s</a>",
                                    routes.TemplateSoccerPlayerController.showStats(templateSoccerPlayer.templateSoccerPlayerId.toString()),
                                    fieldValue);
                    case 7:
                        return String.format("<a href=\"%s\" tabIndex=\"-1\">%s</a>",
                                    routes.TemplateSoccerTeamController.show(templateSoccerPlayer.templateTeamId.toString()),
                                    fieldValue);
                }
                return fieldValue;
            }
        });
    }

    public static Result changeSalary(String templateSoccerPlayerId, Integer salary) {
        Model.templateSoccerPlayers().update(new ObjectId(templateSoccerPlayerId)).with("{$set: {salary: #}}", salary);

        OpsLog.onChange("SALARY", OpsLog.ActingOn.PLAYER, ImmutableMap.of(
                "templateSoccerPlayerId", templateSoccerPlayerId,
                "salary", salary));
        return ok("OK");
    }

    public static Result changeState(String templateSoccerPlayerId, String activated) {
        Model.templateSoccerPlayers().update(new ObjectId(templateSoccerPlayerId)).with("{$set: {activated: #}}", activated.equals("true"));

        OpsLog.onChange("ACTIVATED", OpsLog.ActingOn.PLAYER, ImmutableMap.of(
                "templateSoccerPlayerId", templateSoccerPlayerId,
                "activated", activated));
        return ok("OK");
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

    public static Result showStats(String playerId) {
        TemplateSoccerPlayer templateSoccerPlayer = TemplateSoccerPlayer.findOne(new ObjectId(playerId));
        return ok(views.html.template_soccer_player_stats.render(templateSoccerPlayer));
    }
}
