package controllers.admin;

import model.Model;
import model.opta.*;
import play.Logger;
import play.libs.F;
import play.libs.WS;
import play.mvc.Controller;
import play.mvc.Result;
import utils.ListUtils;

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


    public static F.Promise<Result> importXML(final long last_timestamp){
        F.Promise<Result> resultPromise = WS.url("http://localhost:9000/return_xml/" + last_timestamp).get().map(
                new F.Function<WS.Response, Result>(){
                    public Result apply(WS.Response response){
                        String bodyText  = response.getBody();
                        Date createdAt = new Date(0L);
                        Date lastUpdated = new Date(0L);
                        if (bodyText.equals("NULL")){
                            last_imported = lastUpdated;
                            return ok("-1");
                        } else {
                            java.sql.Connection connection = play.db.DB.getConnection();
                            String headers = response.getHeader("headers");
                            String feedType = response.getHeader("feed-type");
                            String gameId = response.getHeader("game-id");
                            String competitionId = response.getHeader("competition-id");
                            String seasonId = response.getHeader("season-id");
                            createdAt = Model.getDateFromHeader(response.getHeader("created-at"));
                            lastUpdated = Model.getDateFromHeader(response.getHeader("last-updated"));
                            String name = response.getHeader("name");

                            Model.insertXML(connection, bodyText, headers, createdAt, name, feedType, gameId,
                                    competitionId, seasonId, lastUpdated);

                        }
                        last_imported = createdAt;
                        return ok((createdAt).toString());
                    }
                }
        );
        return resultPromise;
    }

    public static Result importFromLast() {
        Date last_date = findLastDate();
        importXML(last_date.getTime());
        while (last_imported.getTime() > 0L) {
            importXML(last_imported.getTime());
        }
        return ok("Finished importing");
    }

    public static Date findLastDate() {
        Statement stmt = null;
        java.sql.Connection connection = play.db.DB.getConnection();
        String selectString = "SELECT created_at FROM dailysoccerdb ORDER BY created_at DESC LIMIT 1;";
        ResultSet results = null;
        try {
            stmt = connection.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE,
                    ResultSet.CONCUR_READ_ONLY);
            results = stmt.executeQuery(selectString);
            if (results.next()) {
                return results.getDate("created_at");
            }
        } catch (java.sql.SQLException e) {
            Logger.error("WTF SQL 92374");
        }
        return null;
    }

    private static Date last_imported;

}
