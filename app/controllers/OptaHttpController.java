package controllers;

import actions.AllowCors;
import model.GlobalDate;
import model.Model;
import model.ModelEvents;
import model.opta.OptaProcessor;
import org.jdom2.input.JDOMParseException;
import org.joda.time.DateTime;
import org.mozilla.universalchardet.UniversalDetector;
import play.Logger;
import play.db.DB;
import play.mvc.BodyParser;
import play.mvc.Controller;
import play.mvc.Result;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.sql.*;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

@AllowCors.Origin
public class OptaHttpController extends Controller {

    @BodyParser.Of(value = BodyParser.TolerantText.class, maxLength = 4 * 1024 * 1024)
    public static Result optaXmlInput() {
        byte[] bodyOriginalBytes = null;
        String bodyText = null;
        String contentType = request().headers().containsKey("Content-Type")?
                                                    request().headers().get("Content-Type")[0]: "";
        String xMetaEncoding = request().headers().containsKey("X-Meta-Encoding")?
                                                    request().headers().get("X-Meta-Encoding")[0]: "UTF-8";

        if (contentType.indexOf("charset=") > 0) {
            bodyText = request().body().asText();
        }
        else {
            try {
                // HTTP default encoding for POST requests is ISO-8859-1 if no charset is passed via "Content-Type" header.
                bodyOriginalBytes = request().body().asText().getBytes("ISO-8859-1");
            }
            catch (UnsupportedEncodingException e) {
                Logger.error("WTF 9151", e);
            }

            String detectedEncoding = null;
            try {
                detectedEncoding = getDetectedEncoding(new ByteArrayInputStream(bodyOriginalBytes));
            }
            catch (IOException e) {
                Logger.error("WTF 1591", e);
            }

            detectedEncoding = detectedEncoding!=null? detectedEncoding : xMetaEncoding;

            try {
                bodyText = new String(bodyOriginalBytes, detectedEncoding);
            }
            catch (UnsupportedEncodingException e) {
                Logger.error("WTF 5119", e);
            }
        }

        Model.insertXML(bodyText,
                        getHeadersString(request().headers()),
                        new Date(),
                        getHeader("X-Meta-Default-Filename", request().headers()),
                        getHeader("X-Meta-Feed-Type", request().headers()),
                        getHeader("X-Meta-Game-Id", request().headers()),
                        getHeader("X-Meta-Competition-Id", request().headers()),
                        getHeader("X-Meta-Season-Id", request().headers()),
                        GlobalDate.parseDate(getHeader("X-Meta-Last-Updated", request().headers()), null));

        OptaProcessor theProcessor = new OptaProcessor();
        HashSet<String> updatedMatchEvents = null;

        try {
            updatedMatchEvents = theProcessor.processOptaDBInput(getHeader("X-Meta-Feed-Type", request().headers()), bodyText);
        }
        catch (JDOMParseException e) {
            Logger.error("Exception parsing: {}", getHeader("X-Meta-Default-Filename", request().headers()), e);
        }

        ModelEvents.onOptaMatchEventIdsChanged(updatedMatchEvents);

        return ok("Yeah, XML processed");
    }

    private static String getDetectedEncoding(InputStream is) throws IOException {

        UniversalDetector detector = new UniversalDetector(null);
        byte[] buf = new byte[4096];
        int nread;
        while ((nread = is.read(buf)) > 0 && !detector.isDone()) {
            detector.handleData(buf, 0, nread);
        }
        detector.dataEnd();

        String encoding = detector.getDetectedCharset();

        if (encoding != null) {
            Logger.info("Detected enconding: {}", encoding);
        }
        else {
            Logger.error("Encoding not detected properly");
        }

        return encoding;
    }

    private static String getHeadersString(Map<String, String[]> headers) {
        Map<String, String> plainHeaders = new HashMap<String, String>();
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

        Date askedDate = new Date(last_timestamp);

        String retXML = "NULL";

        try (Connection connection = DB.getConnection()) {

            ResultSet nextOptaData = findXML(connection, askedDate);

            if (nextOptaData != null && nextOptaData.next()) {
                setResponseHeaders(nextOptaData);
                retXML = nextOptaData.getString("xml");
            }
        }
        catch (java.sql.SQLException e) {
            Logger.error("WTF 52683", e);
        }

        return ok(retXML);
    }

    public static Result dateLastXML() {
        return ok(GlobalDate.formatDate(Model.getLastDateFromOptaXML()));
    }

    public static Result remainingXMLs(long last_timestamp) {

        Date askedDate = new Date(last_timestamp);
        String remainingXML = "0";

        try (Connection connection = DB.getConnection()) {

            ResultSet remainingOptaData = getRemaining(connection, askedDate);

            if (remainingOptaData != null && remainingOptaData.next()) {
                remainingXML = String.valueOf(remainingOptaData.getInt("remaining"));
            }
        }
        catch (java.sql.SQLException e) {
            Logger.error("WTF 25386", e);
        }

        return ok(remainingXML);

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

    private static ResultSet findXML(Connection connection, Date askedDate) throws SQLException {

        Timestamp last_date = new Timestamp(askedDate.getTime());
        String selectString = "SELECT * FROM optaxml WHERE created_at > '"+last_date+"' ORDER BY created_at LIMIT 1;";

        Statement stmt = connection.createStatement(ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY);
        return stmt.executeQuery(selectString);
    }

    private static ResultSet getRemaining(Connection connection, Date askedDate) throws SQLException {
        Timestamp last_date = new Timestamp(askedDate.getTime());
        String selectString = "SELECT count(*) as remaining FROM optaxml WHERE created_at > '"+last_date+"';";

        Statement stmt = connection.createStatement(ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY);
        return stmt.executeQuery(selectString);
    }
}
