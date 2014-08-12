package controllers.admin;

import model.GlobalDate;
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

public class RefresherController extends Controller {

    public static Result index() {
        return ok(views.html.refresher.render());
    }


    public static Result inProgress() {
        return ok(String.valueOf(_inProgress));
    }

    public static Result importFromLast() {

        if (_inProgress)
            return ok("Already refreshing");

        _inProgress = true;

        long last_date = findLastDate().getTime();

        while (last_date >= 0) {
            Logger.debug("Importing xml date: " + last_date);
            last_date = downloadAndImportXML(last_date);
        }

        _inProgress = false;

        return ok("Finished importing");
    }

    private static Date findLastDate() {

        String selectString = "SELECT created_at FROM optaxml ORDER BY created_at DESC LIMIT 1;";
        ResultSet results = null;

        try (Connection connection = DB.getConnection()) {
            try (Statement stmt = connection.createStatement()) {
                results = stmt.executeQuery(selectString);

                if (results.next()) {
                    return results.getTimestamp("created_at");
                }
            }
        }
        catch (java.sql.SQLException e) {
            Logger.error("WTF SQL 92374");
        }

        return new Date(0L);
    }

    public static Result lastDate() {
        return ok(String.valueOf(findLastDate().getTime()));    // Returns date in millis
    }

    private static long downloadAndImportXML(long last_timestamp) {

        F.Promise<WS.Response> responsePromise = WS.url("http://dailysoccer.herokuapp.com/return_xml/" + last_timestamp).get();
        WS.Response response = responsePromise.get(100000);

        if (response.getStatus() != 200) {
            Logger.error("Response not OK: " + response.getStatus());
            return -2L;
        }
        else {
            String bodyText = response.getBody();

            if (bodyText.equals("NULL")) {
                return -1L;
            }
            else {
                String headers = response.getHeader("headers");
                String feedType = response.getHeader("feed-type");
                String gameId = response.getHeader("game-id");
                String competitionId = response.getHeader("competition-id");
                String seasonId = response.getHeader("season-id");
                Date createdAt = GlobalDate.parseDate(response.getHeader("created-at"), null);
                Date lastUpdated = GlobalDate.parseDate(response.getHeader("last-updated"), null);
                String name = response.getHeader("name");

                if (createdAt.after(new Date(last_timestamp))) {
                    Model.insertXML(bodyText, headers, createdAt, name, feedType, gameId, competitionId, seasonId, lastUpdated);
                    return createdAt.getTime();
                }
                else {
                    return -2L;
                }
            }
        }
    }

    public static boolean _inProgress = false;
}
