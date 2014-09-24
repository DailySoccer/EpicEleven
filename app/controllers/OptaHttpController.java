package controllers;

import actions.AllowCors;
import model.GlobalDate;
import model.opta.OptaXmlUtils;
import org.joda.time.DateTime;
import play.Logger;
import play.mvc.BodyParser;
import play.mvc.Controller;
import play.mvc.Result;
import utils.DbSqlUtils;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

@AllowCors.Origin
public class OptaHttpController extends Controller {

    @BodyParser.Of(value = BodyParser.TolerantText.class, maxLength = 4 * 1024 * 1024)
    public static Result optaXmlInput() {

        String bodyText = null;

        if (!request().headers().containsKey("X-Meta-Encoding") ||
            !request().headers().get("X-Meta-Encoding")[0].equals("UTF-8")) {
            Logger.error("WTF 0921: Nos ha llegado un fichero de Opta con codificacion no esperada. Asumimos UTF-8");
        }

        try {
            // HTTP default encoding for POST requests is ISO-8859-1 if no charset is passed via "Content-Type" header.
            bodyText = new String(request().body().asText().getBytes("ISO-8859-1"), "UTF-8");

            String fileName = getHeader("X-Meta-Default-Filename", request().headers());
            Logger.info("About to insert {}", fileName);

            OptaXmlUtils.insertXml(bodyText, getHeadersString(request().headers()), new Date(), fileName,
                                   getHeader("X-Meta-Feed-Type", request().headers()),
                                   getHeader("X-Meta-Game-Id", request().headers()),
                                   getHeader("X-Meta-Competition-Id", request().headers()),
                                   getHeader("X-Meta-Season-Id", request().headers()),
                                   GlobalDate.parseDate(getHeader("X-Meta-Last-Updated", request().headers()), null));
        }
        catch (Exception e) {
            Logger.error("WTF 5119", e);
        }

        return ok("Yeah, XML inserted");
    }

    private static String getHeadersString(Map<String, String[]> headers) {
        Map<String, String> plainHeaders = new HashMap<>();
        for (String key: headers.keySet()){
            plainHeaders.put(key, "'"+headers.get(key)[0]+"'");
        }
        return plainHeaders.toString();
    }

    private static String getHeader(String key, Map<String, String[]> headers) {
        if (null == headers) {
            return null;
        }
        return headers.containsKey(key)?
                    headers.get(key)[0]:
                    headers.containsKey(key.toLowerCase())?
                            headers.get(key.toLowerCase())[0]:
                            null;
    }

    public static Result returnXML(long last_timestamp) {
        final String nullXML = "NULL";

        return ok(OptaXmlUtils.readNextXmlByDate(new Date(last_timestamp), new DbSqlUtils.IResultSetReader<String>() {

            public String handleResultSet(ResultSet resultSet) throws SQLException {

                if (resultSet.next()) {
                    setResponseHeaders(resultSet);
                    return resultSet.getString("xml");
                }

                return nullXML;
            }

            public String handleSQLException() {
                return nullXML;
            }
        }));
    }

    public static Result dateLastXML() {
        return ok(GlobalDate.formatDate(OptaXmlUtils.getLastDate()));
    }

    public static Result remainingXMLs(long last_timestamp) {
        return ok(String.valueOf(OptaXmlUtils.getRemainingXmlCount(new Date(last_timestamp))));
    }

    private static void setResponseHeaders(ResultSet nextOptaData) throws SQLException {

        String headers = nextOptaData.getString("headers");
        String feedType = nextOptaData.getString("feed_type");
        String name = nextOptaData.getString("name");
        String gameId = nextOptaData.getString("game_id");
        String competitionId = nextOptaData.getString("competition_id");
        String seasonId = nextOptaData.getString("season_id");
        Timestamp lastUpdated = nextOptaData.getTimestamp("last_updated");
        Timestamp createdAt = nextOptaData.getTimestamp("created_at");

        response().setHeader("headers", headers);

        if (name != null) {
            response().setHeader("name", name);
        }
        if (gameId != null) {
            response().setHeader("game-id", gameId);
        }
        if (competitionId != null) {
            response().setHeader("competition-id", competitionId);
        }
        if (seasonId != null) {
            response().setHeader("season-id", seasonId);
        }
        if (feedType != null) {
            response().setHeader("feed-type", feedType);
        }
        if (createdAt != null) {
            response().setHeader("created-at", new DateTime(createdAt).toString());
        }
        if (lastUpdated != null) {
            response().setHeader("last-updated", new DateTime(lastUpdated).toString());
        }
    }
}
