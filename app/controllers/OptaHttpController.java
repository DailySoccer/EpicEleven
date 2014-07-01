package controllers;

import actions.AllowCors;
import com.mongodb.*;
import com.mongodb.util.JSON;
import model.Model;
import model.opta.*;
import org.bson.types.ObjectId;
import org.json.XML;
import play.Logger;
import play.libs.F;
import play.libs.WS;
import play.mvc.BodyParser;
import play.mvc.Controller;
import play.mvc.Result;
import utils.OptaUtils;
import play.db.DB;


import javax.sql.DataSource;
import java.sql.*;
import java.io.UnsupportedEncodingException;
import java.util.*;
import java.util.Date;

/**
 * Created by gnufede on 30/05/14.
 */
@AllowCors.Origin
public class OptaHttpController extends Controller {
    @BodyParser.Of(value = BodyParser.TolerantText.class, maxLength = 4 * 1024 * 1024)
    public static Result optaXmlInput(){

        long startDate = System.currentTimeMillis();
        String bodyText = request().body().asText();
        try {
            bodyText = new String(bodyText.getBytes("ISO-8859-1"));
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
        BasicDBObject bodyAsJSON = (BasicDBObject) JSON.parse("{}");
        String name = "default-filename";
        try {
            if (request().headers().containsKey("x-meta-default-filename")){
                name = request().headers().get("x-meta-default-filename")[0];
            }
            else if (request().headers().containsKey("X-Meta-Default-Filename")){
                name = request().headers().get("X-Meta-Default-Filename")[0];
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        try {
            bodyText = bodyText.substring(bodyText.indexOf('<'));
            // No hay manera de pasar de JSON a BSON directamente al parecer, sin pasar por String,
            // o por un hashmap (que tampoco parece trivial)
            // http://stackoverflow.com/questions/5699323/using-json-with-mongodb
            bodyAsJSON = (BasicDBObject) JSON.parse(XML.toJSONObject(bodyText).toString());
        } catch (Exception e) {
            e.printStackTrace();
        }

        Connection connection = DB.getConnection();
        Statement stmt = null;
        String insertString = "INSERT INTO optadb VALUES ( "+new Date(startDate).toString()+", "+bodyText+" ) "
        try {
            stmt = connection.prepareStatement(insertString);
            boolean result = stmt.execute();
            if (result){
                Logger.info("Inserción en OptaDB");
            }
        }
        catch (java.sql.SQLException e) {
            Logger.error("SQL Exception connecting to OptaDB");
            e.printStackTrace();
        }
        finally {
            if (stmt != null) {
                try {
                    stmt.close();
                } catch (SQLException e) {
                    Logger.error("SQL Exception closing Postgres statement");
                    e.printStackTrace();
                }
            }
        }

        Model.optaDB().insert(new OptaDB(bodyText,
                bodyAsJSON,
                name,
                request().headers(),
                startDate,
                System.currentTimeMillis()));
        String feedType = null;
        if (request().headers().containsKey("X-Meta-Feed-Type")) {
            feedType = request().headers().get("X-Meta-Feed-Type")[0];
            OptaUtils.processOptaDBInput(feedType, bodyAsJSON);
        }
        return ok("Yeah, XML processed");
    }
}
