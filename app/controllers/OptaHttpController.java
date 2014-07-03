package controllers;

import actions.AllowCors;
import com.mongodb.*;
import com.mongodb.util.JSON;
import model.Model;
import model.ModelCoreLoop;
import model.opta.*;
import org.json.XML;
import play.mvc.BodyParser;
import play.mvc.Controller;
import play.mvc.Result;
import model.opta.OptaProcessor;


import java.io.UnsupportedEncodingException;
import java.util.HashSet;

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
        }
        catch (UnsupportedEncodingException e) {
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
        }
        catch (Exception e) {
            e.printStackTrace();
        }

        try {
            bodyText = bodyText.substring(bodyText.indexOf('<'));
            // No hay manera de pasar de JSON a BSON directamente al parecer, sin pasar por String,
            // o por un hashmap (que tampoco parece trivial)
            // http://stackoverflow.com/questions/5699323/using-json-with-mongodb
            bodyAsJSON = (BasicDBObject) JSON.parse(XML.toJSONObject(bodyText).toString());
        }
        catch (Exception e) {
            e.printStackTrace();
        }

        Model.optaDB().insert(new OptaDB(bodyText, bodyAsJSON, name, request().headers(), startDate, System.currentTimeMillis()));

        if (request().headers().containsKey("X-Meta-Feed-Type")) {
            String feedType = request().headers().get("X-Meta-Feed-Type")[0];

            OptaProcessor theProcessor = new OptaProcessor();
            HashSet<String> dirtyMatchEvents = theProcessor.processOptaDBInput(feedType, bodyAsJSON);
            ModelCoreLoop.onOptaMatchEventsChanged(dirtyMatchEvents);
        }
        return ok("Yeah, XML processed");
    }
}
