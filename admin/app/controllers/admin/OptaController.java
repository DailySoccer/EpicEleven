package controllers.admin;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import model.GlobalDate;
import model.MockData;
import model.Model;
import model.OpsLog;
import model.opta.*;
import play.mvc.Controller;
import play.mvc.Result;
import utils.ListUtils;
import utils.PaginationData;

import java.util.List;

public class OptaController extends Controller {
    public static Result optaCompetitions() {
        List<OptaCompetition> optaCompetitions = ListUtils.asList(Model.optaCompetitions().find().as(OptaCompetition.class));
        return ok(views.html.opta_competition_list.render(optaCompetitions));
    }

    public static Result changeCompetitionState(String seasonCompetitionId, String state) {
        Model.optaCompetitions().update("{seasonCompetitionId: #}", seasonCompetitionId).with("{$set: {activated: #}}", state.toLowerCase().equals("true"));

        OpsLog.onChange(OpsLog.ActingOn.COMPETITION, ImmutableMap.of(
                "seasonCompetitionId", seasonCompetitionId,
                "activated", state));
        return ok("OK");
    }

    public static Result createAllCompetitions() {
        MockData.ensureCompetitions();
        return optaCompetitions();
    }

    public static Result optaSoccerPlayers() {
        Iterable<OptaPlayer> optaPlayerResults = Model.optaPlayers().find().as(OptaPlayer.class);
        List<OptaPlayer> optaPlayerList = ListUtils.asList(optaPlayerResults);

        return ok(views.html.opta_soccer_player_list.render(optaPlayerList));
    }

    public static Result optaSoccerTeams() {
        Iterable<OptaTeam> optaTeamResults = Model.optaTeams().find().as(OptaTeam.class);
        List<OptaTeam> optaTeamList = ListUtils.asList(optaTeamResults);

        return ok(views.html.opta_soccer_team_list.render(optaTeamList));
    }

    public static Result optaMatchEvents() {
        Iterable<OptaMatchEvent> optaMatchEventResults = Model.optaMatchEvents().find().as(OptaMatchEvent.class);
        List<OptaMatchEvent> optaMatchEventList = ListUtils.asList(optaMatchEventResults);

        return ok(views.html.opta_match_event_list.render(optaMatchEventList));
    }

    public static Result optaEvents() {
        return ok(views.html.opta_event_list.render());
    }

    public static Result optaEventsAjax() {
         return PaginationData.withAjax(request().queryString(), Model.optaEvents(), OptaEvent.class, new PaginationData() {
            public List<String> getFieldNames() {
                return ImmutableList.of(
                        "pointsTranslationId",
                        "competitionId",
                        "gameId",
                        "optaPlayerId",
                        "typeId",
                        "points",
                        "timestamp",
                        "lastModified"
                );
            }

            public String getFieldByIndex(Object data, Integer index) {
                OptaEvent optaEvent = (OptaEvent) data;
                switch (index) {
                    case 0: return optaEvent.pointsTranslationId != null ? optaEvent.pointsTranslationId.toString() : "-";
                    case 1: return optaEvent.competitionId;
                    case 2: return optaEvent.gameId;
                    case 3: return optaEvent.optaPlayerId;
                    case 4: return String.valueOf(optaEvent.typeId);
                    case 5: return String.valueOf(optaEvent.points);
                    case 6: return GlobalDate.formatDate(optaEvent.timestamp);
                    case 7: return GlobalDate.formatDate(optaEvent.lastModified);
                }
                return "<invalid value>";
            }
        });
    }

    public static Result updateOptaEvents() {

        new OptaProcessor().recalculateAllEvents();

        FlashMessage.success("All OptaEvents recalculated with the current points translation table");
        return redirect(routes.PointsTranslationController.index());
    }


}
