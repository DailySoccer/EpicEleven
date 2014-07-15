package controllers;

import actions.AllowCors;
import model.Model;
import model.ModelEvents;
import model.opta.OptaDB;
import model.opta.OptaProcessor;
import play.Logger;
import play.db.DB;
import play.mvc.BodyParser;
import play.mvc.Controller;
import play.mvc.Result;

import java.io.UnsupportedEncodingException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

/**
 * Created by gnufede on 30/05/14.
 */
@AllowCors.Origin
public class OptaHttpController extends Controller {
    @BodyParser.Of(value = BodyParser.TolerantText.class, maxLength = 4 * 1024 * 1024)
    public static Result optaXmlInput() {

        long startDate = System.currentTimeMillis();
        String bodyText = request().body().asText();
        try {
            bodyText = new String(bodyText.getBytes("ISO-8859-1"));
        }
        catch (UnsupportedEncodingException e) {
            Logger.error("WTF 43451: ", e);
        }

        String name = "default-filename";

        try {
            if (request().headers().containsKey("x-meta-default-filename")){
                name = request().headers().get("x-meta-default-filename")[0];
            }
            else if (request().headers().containsKey("X-Meta-Default-Filename")){
                name = request().headers().get("X-Meta-Default-Filename")[0];
            }
        }
        catch (Exception e) {
            Logger.error("WTF 43859: ", e);
        }

        try {
            bodyText = bodyText.substring(bodyText.indexOf('<'));
            // No hay manera de pasar de JSON a BSON directamente al parecer, sin pasar por String,
            // o por un hashmap (que tampoco parece trivial)
            // http://stackoverflow.com/questions/5699323/using-json-with-mongodb
            //bodyAsJSON = (BasicDBObject) JSON.parse(XML.toJSONObject(bodyText).toString());
        }
        catch (Exception e) {
            e.printStackTrace();
        }
        Connection connection = DB.getConnection();

        insertXML(connection, bodyText,
                  getHeadersString(request().headers()),
                  new Date(startDate),
                  getHeader("X-Meta-Default-Filename", request().headers()),
                  getHeader("X-Meta-Feed-Type", request().headers()),
                  getHeader("X-Meta-Game-Id", request().headers()),
                  getHeader("X-Meta-Competition-Id", request().headers()),
                  getHeader("X-Meta-Season-Id", request().headers()),
                  Model.getDateFromHeader(getHeader("X-Meta-Last-Updated", request().headers()))
                 );

        OptaProcessor theProcessor = new OptaProcessor();
        //HashSet<String> dirtyMatchEvents = theProcessor.processOptaDBInput(getHeader("X-Meta-Feed-Type", request().headers()), bodyAsJSON);
        HashSet<String> dirtyMatchEvents = theProcessor.processOptaDBInput(getHeader("X-Meta-Feed-Type", request().headers()), bodyText);
        ModelEvents.onOptaMatchEventIdsChanged(dirtyMatchEvents);

        return ok("Yeah, XML processed");
    }

    public static String getHeadersString(Map<String, String[]> headers) {
        Map<String, String> plainHeaders = new HashMap<String, String>();
        for (String key: headers.keySet()){
            plainHeaders.put(key, "'"+headers.get(key)[0]+"'");
        }
        return plainHeaders.toString();
    }

    public static String getHeader(String key, Map<String, String[]> headers) {
        if (null == headers) {
            return null;
        }
        return headers.containsKey(key)?
                    headers.get(key)[0]:
                    headers.containsKey(key.toLowerCase())?
                            headers.get(key.toLowerCase())[0]:
                            null;
    }

    public static Result migrate(){
        Iterable<OptaDB> allOptaDBs = Model.optaDB().find().as(OptaDB.class);
        Connection connection = DB.getConnection();

        for (OptaDB document: allOptaDBs) {
            if (getHeader("X-Meta-Feed-Type", document.headers) != null) {

                insertXML(connection, document.xml, getHeadersString(document.headers), new Date(document.startDate), document.name,
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

    public static Result returnXML(long last_timestamp){
        ResultSet nextOptaData = findXML(last_timestamp);
        String headers = "";
        Date createdAt, lastUpdated;
        String name, feedType, gameId, competitionId, seasonId, xml = "";
        if (nextOptaData == null) {
            return ok("NULL");
        }
        try {
            if (nextOptaData.next()){
                headers = nextOptaData.getString("headers");
                feedType = nextOptaData.getString("feed_type");
                name = nextOptaData.getString("name");
                gameId = nextOptaData.getString("game_id");
                competitionId = nextOptaData.getString("competition_id");
                seasonId = nextOptaData.getString("season_id");
                lastUpdated = nextOptaData.getTimestamp("last_updated");
                createdAt = nextOptaData.getTimestamp("created_at");
                xml = nextOptaData.getSQLXML("xml").getString();

                response().setHeader("headers", headers);
                response().setHeader("name", name);
                response().setHeader("game-id", gameId);
                response().setHeader("competition-id", competitionId);
                response().setHeader("season-id", seasonId);
                response().setHeader("feed-type", feedType);
                response().setHeader("created-at", createdAt.toString());
                response().setHeader("last-updated", lastUpdated.toString());

            }
        } catch (java.sql.SQLException e) {
            Logger.error("WTF SQL 5683");
        }
        response().setContentType("text/html");

        return ok(xml);
    }

    public static ResultSet findXML(long last_timestamp) {
        Statement stmt = null;
        Connection connection = DB.getConnection();
        Date last_date = new Date(last_timestamp);
        String selectString = "SELECT * FROM optaxml WHERE created_at > '"+last_date+"' ORDER BY created_at LIMIT 1;";
        ResultSet results = null;
        try {
            stmt = connection.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE,
                    ResultSet.CONCUR_READ_ONLY);

            results = stmt.executeQuery(selectString);

        } catch (java.sql.SQLException e) {
            Logger.error("SQL Exception connecting to DailySoccerDB");
            e.printStackTrace();
        }
        return results;
    }

    public static void insertXML(Connection connection, String xml, String headers, Date timestamp, String name, String feedType,
                                 String gameId, String competitionId, String seasonId, Date lastUpdated) {

        String insertString = "INSERT INTO optaxml (xml, headers, created_at, name, feed_type, game_id, competition_id," +
                "season_id, last_updated) VALUES ( XMLPARSE (DOCUMENT ?),?,?,?,?,?,?,?,?)";

        try {
            try (PreparedStatement stmt = connection.prepareStatement(insertString)) {
                stmt.setString(1, xml);
                stmt.setString(2, headers);
                stmt.setTimestamp(3, new java.sql.Timestamp(timestamp.getTime()));
                stmt.setString(4, name);
                stmt.setString(5, feedType);
                stmt.setString(6, gameId);
                stmt.setString(7, competitionId);
                stmt.setString(8, seasonId);

                if (lastUpdated != null) {
                    stmt.setTimestamp(9, new java.sql.Timestamp(lastUpdated.getTime()));
                } else {
                    stmt.setTimestamp(9, null);
                }

                if (stmt.execute()) {
                    Logger.info("Successful insert in OptaXML");
                }
            }
        }
        catch (java.sql.SQLException e) {
            Logger.error("WTF 56312: ", e);
        }
    }
}
