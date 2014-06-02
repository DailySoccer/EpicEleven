package controllers;

import com.mongodb.BasicDBList;
import com.mongodb.BasicDBObject;
import com.mongodb.DBObject;
import com.mongodb.util.JSON;
import model.Model;
import model.opta.OptaDB;
import model.opta.OptaPlayer;
import org.jongo.Find;
import org.json.XML;
import play.mvc.BodyParser;
import play.mvc.Controller;
import play.mvc.Result;

import play.libs.Json;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;

/**
 * Created by gnufede on 30/05/14.
 */
public class OptaHttpController extends Controller {
    @BodyParser.Of(value = BodyParser.TolerantText.class, maxLength = 4 * 1024 * 1024)
    public static Result optaXmlInput(){
        long startDate = System.currentTimeMillis();
        String bodyText = request().body().asText();
        DBObject bodyAsJSON = (DBObject) JSON.parse("{}");
        String name = "default-filename";
        try {
            name = request().headers().get("x-meta-default-filename")[0];
            bodyText = bodyText.substring(bodyText.indexOf('<'));
            bodyAsJSON = (DBObject) JSON.parse(XML.toJSONObject(bodyText).toString());
        } catch (Exception e) {
            e.printStackTrace();
        }
        Model.optaDB().insert(new OptaDB(bodyText,
                bodyAsJSON,
                name,
                request().headers(),
                startDate,
                System.currentTimeMillis()));

        if (bodyAsJSON.containsField("FANTASY")){
            processFantasy(bodyAsJSON);
        }

        return ok("Yeah, XML processed");
    }


    public static void processFantasy(DBObject game){
        BasicDBList teams = (BasicDBList)game.get("Team");

        for (Object team: teams){
            BasicDBObject teamObject = (BasicDBObject)team;
            BasicDBList playersList = (BasicDBList)teamObject.get("Player");

            for (Object player: playersList){
                BasicDBObject playerObject = (BasicDBObject)player;
                OptaPlayer my_player = new OptaPlayer();
                my_player.firstname = (String) playerObject.get("firstname");
                my_player.lastname = (String) playerObject.get("lastname");
                my_player.position = (String) playerObject.get("position");
                my_player.id = (int) playerObject.get("id");
                my_player.teamid = (int) teamObject.get("id");
                my_player.teamname = (String) teamObject.get("name");
                Model.optaPlayers().insert(my_player);
            }
        }
    }

    public static Result parseFantasy(){
        DBObject game = (DBObject)Model.optaDB().find("{'FANTASY': {$exists: true}}").sort("{'_id': -1}").limit(1);
        processFantasy(game);
        return ok("Yeah, Fantasy processed");
    }

}
