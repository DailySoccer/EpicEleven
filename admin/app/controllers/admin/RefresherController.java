package controllers.admin;

import model.Model;
import play.Logger;
import play.db.DB;
import play.libs.F;
import play.libs.WS;
import play.mvc.Controller;
import play.mvc.Result;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Date;

/**
 * Created by gnufede on 18/07/14.
 */
public class RefresherController extends Controller {

    public static Result index() {
        return ok(views.html.refresher.render());
    }

    public static long importXML(long last_timestamp) {
        F.Promise<WS.Response> response = WS.url("http://dailysoccer.herokuapp.com/return_xml/" + last_timestamp).get();
        WS.Response a = response.get(100000);
        return processXML(a, new Date(last_timestamp));
    }

    private static long processXML(final WS.Response response, Date lastDate) {
        if (response.getStatus() != 200) {
            Logger.error("Response not OK: " + response.getStatus());
            return -2L;
        } else {
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

                if (createdAt.after(lastDate)) {
                    Model.insertXML(bodyText, headers, createdAt, name, feedType, gameId,
                                    competitionId, seasonId, lastUpdated);
                    return createdAt.getTime();
                } else {
                    return -2L;
                }

            }
        }
    }

    public static Result importFromLast() {
        Logger.debug("Starting at: {}", findLastDate());
        for (long last_date = findLastDate().getTime(); 0L <= last_date; last_date=importXML(last_date)) {
            Logger.debug("once again: "+last_date);
        }
        Logger.debug("Finished at: {}", findLastDate());
        return ok("Finished importing");
    }

    public static Date findLastDate() {
        String selectString = "SELECT created_at FROM optaxml ORDER BY created_at DESC LIMIT 1;";
        ResultSet results = null;
        try (Connection connection = DB.getConnection()) {
            try (Statement stmt = connection.createStatement()) {
                results = stmt.executeQuery(selectString);
                if (results.next()) {
                    return results.getTimestamp("created_at");
                }
            }
        } catch (java.sql.SQLException e) {
            Logger.error("WTF SQL 92374");
        }
        return new Date(0L);
    }

    public static Result lastDate() {
        return ok(String.valueOf(findLastDate().getTime()));
    }

}
