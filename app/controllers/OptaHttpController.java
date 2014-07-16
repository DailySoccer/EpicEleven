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
import java.sql.*;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.*;

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

        Model.insertXML(bodyText,
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

    public static Result returnXML(long last_timestamp){
        String xml = "";
        try (Connection connection = DB.getConnection()) {
            ResultSet nextOptaData = findXML(connection, last_timestamp);
            String headers = "";
            Timestamp createdAt, lastUpdated;
            String name, feedType, gameId, competitionId, seasonId = "";
            SimpleDateFormat format1 = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS");
            format1.setTimeZone(TimeZone.getTimeZone("UTC"));
            if (nextOptaData == null) {
                return ok("NULL");
            }
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
                response().setHeader("created-at", format1.format(createdAt));
                response().setHeader("last-updated", format1.format(lastUpdated));
            }

        } catch (java.sql.SQLException e) {
            Logger.error("WTF 52683");
        }
        response().setContentType("text/html");

        return ok(xml);
    }

    public static ResultSet findXML(Connection connection, long last_timestamp) {
        Timestamp last_date = new Timestamp(last_timestamp);
        String selectString = "SELECT * FROM optaxml WHERE created_at > '"+last_date+"' ORDER BY created_at LIMIT 1;";
        Logger.debug(selectString);
        ResultSet results = null;
            try (Statement stmt = connection.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_READ_ONLY)) {
                results = stmt.executeQuery(selectString);
            }
        catch (java.sql.SQLException e) {
            Logger.error("WTF 72613", e);
        }
        return results;
    }

}
