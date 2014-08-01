package controllers;

import actions.AllowCors;
import model.Model;
import model.ModelEvents;
import model.opta.OptaDB;
import model.opta.OptaProcessor;
import org.jdom2.input.JDOMParseException;
import org.mozilla.universalchardet.UniversalDetector;
import play.Logger;
import play.db.DB;
import play.mvc.BodyParser;
import play.mvc.Controller;
import play.mvc.Result;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.sql.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.Date;

/**
 * Created by gnufede on 30/05/14.
 */
@AllowCors.Origin
public class OptaHttpController extends Controller {

    @BodyParser.Of(value = BodyParser.TolerantText.class, maxLength = 4 * 1024 * 1024)
    public static Result optaXmlInput() {

        String bodyText = null;
        InputStream stream = new ByteArrayInputStream(request().body().asRaw().asBytes());

        try {
            String encoding = getDetectedEncoding(stream);
            // Read with detected encoding, defaults to ISO
            assert encoding != null;
            bodyText = new String(request().body().asRaw().asBytes(), encoding);
        }
        catch (IOException e) {
            Logger.error("WTF 1783");
        }

        assert bodyText != null;

        Model.insertXML(bodyText,
                        getHeadersString(request().headers()),
                        new Date(System.currentTimeMillis()),
                        getHeader("X-Meta-Default-Filename", request().headers()),
                        getHeader("X-Meta-Feed-Type", request().headers()),
                        getHeader("X-Meta-Game-Id", request().headers()),
                        getHeader("X-Meta-Competition-Id", request().headers()),
                        getHeader("X-Meta-Season-Id", request().headers()),
                        Model.getDateFromHeader(getHeader("X-Meta-Last-Updated", request().headers())));

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
        } else {
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

    public static Result migrate() {
        Iterable<OptaDB> allOptaDBs = Model.optaDB().find().as(OptaDB.class);

        for (OptaDB document: allOptaDBs) {
            if (getHeader("X-Meta-Feed-Type", document.headers) != null) {

                Model.insertXML(document.xml, getHeadersString(document.headers), new Date(document.startDate), document.name,
                                getHeader("X-Meta-Feed-Type", document.headers), getHeader("X-Meta-Game-Id", document.headers),
                                getHeader("X-Meta-Competition-Id", document.headers), getHeader("X-Meta-Season-Id", document.headers),
                                Model.getDateFromHeader(getHeader("X-Meta-Last-Updated", document.headers)));
            }
            else {
                Logger.debug("IGNORANDO: " + document.name);
            }
        }
        return ok("Migrating...");
    }

    public static Result returnXML(long last_timestamp) {

        SimpleDateFormat format1 = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS");
        format1.setTimeZone(TimeZone.getTimeZone("UTC"));
        Date askedDate = new Date(last_timestamp);

        String retXML = "NULL";

        try (Connection connection = DB.getConnection()) {

            ResultSet nextOptaData = findXML(connection, askedDate);

            if (nextOptaData != null && nextOptaData.next()) {
                setResponseHeaders(nextOptaData, format1);
                retXML = nextOptaData.getString("xml");
            }
        }
        catch (java.sql.SQLException e) {
            Logger.error("WTF 52683", e);
            Logger.info("Possibly end of documents reached: {}", format1.format(askedDate));
        }

        response().setContentType("text/html");

        return ok(retXML);
    }

    @AllowCors.Origin
    public static Result dateLastXML() {
        return ok(Model.dateLastFromOptaXML().toString());
    }

    @AllowCors.Origin
    public static Result remainingXMLs(long last_timestamp) {
        SimpleDateFormat format1 = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS");
        format1.setTimeZone(TimeZone.getTimeZone("UTC"));
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

        response().setContentType("text/html");

        return ok(remainingXML);

    }

    private static void setResponseHeaders(ResultSet nextOptaData, SimpleDateFormat dateFormat) throws SQLException {
        Timestamp createdAt, lastUpdated;
        String name, feedType, gameId, competitionId, seasonId = "", headers = "";

        headers = nextOptaData.getString("headers");
        feedType = nextOptaData.getString("feed_type");
        name = nextOptaData.getString("name");
        gameId = nextOptaData.getString("game_id");
        competitionId = nextOptaData.getString("competition_id");
        seasonId = nextOptaData.getString("season_id");
        lastUpdated = nextOptaData.getTimestamp("last_updated");
        createdAt = nextOptaData.getTimestamp("created_at");

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
            response().setHeader("created-at", dateFormat.format(createdAt));
        }
        if (lastUpdated != null) {
            response().setHeader("last-updated", dateFormat.format(lastUpdated));
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
