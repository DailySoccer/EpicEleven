package controllers.admin;

import model.Model;
import model.opta.*;
import play.Logger;
import play.db.DB;
import play.libs.F;
import play.libs.WS;
import play.mvc.Controller;
import play.mvc.Result;
import utils.ListUtils;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Date;
import java.util.List;

public class OptaController extends Controller {
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
        Iterable<OptaEvent> optaEventResults = Model.optaEvents().find().as(OptaEvent.class);
        List<OptaEvent> optaEventList = ListUtils.asList(optaEventResults);

        return ok(views.html.opta_event_list.render(optaEventList));
   }

    public static Result updateOptaEvents() {

        new OptaProcessor().recalculateAllEvents();

        FlashMessage.success("All OptaEvents recalculated with the current points translation table");
        return redirect(routes.PointsTranslationController.index());
    }


    public static long importXML(long last_timestamp) {
        F.Promise<WS.Response> response = WS.url("http://dailysoccer-staging.herokuapp.com/return_xml/" + last_timestamp).get();
        WS.Response a = response.get(100000);
        return processXML(a);
    }

    private static long processXML(final WS.Response response) {
        String bodyText  = response.getBody();
        Date createdAt = new Date(0L);
        Date lastUpdated = new Date(0L);
        if (bodyText.equals("NULL")) {
            return -1L;
        } else {
            String headers = response.getHeader("headers");
            String feedType = response.getHeader("feed-type");
            String gameId = response.getHeader("game-id");
            String competitionId = response.getHeader("competition-id");
            String seasonId = response.getHeader("season-id");
            createdAt = Model.getDateFromHeader(response.getHeader("created-at"));
            lastUpdated = Model.getDateFromHeader(response.getHeader("last-updated"));
            String name = response.getHeader("name");

            Model.insertXML(bodyText, headers, createdAt, name, feedType, gameId,
                    competitionId, seasonId, lastUpdated);

            return createdAt.getTime();
        }
    }

    public static Result importFromLast() {
        for (long last_date = findLastDate().getTime(); 0L <= last_date; last_date=importXML(last_date)) {
            Logger.debug("once again: "+last_date);
        }
        return ok("Finished importing");
    }

    public static Date findLastDate() {
        String selectString = "SELECT created_at FROM optaxml ORDER BY created_at DESC LIMIT 1;";
        ResultSet results = null;
        try (Connection connection = DB.getConnection()) {
            try (Statement stmt = connection.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_READ_ONLY)) {
                results = stmt.executeQuery(selectString);
                if (results.next()) {
                    return results.getDate("created_at");
                }
            }
        } catch (java.sql.SQLException e) {
            Logger.error("WTF SQL 92374");
        }
        return new Date(0L);
    }

}
