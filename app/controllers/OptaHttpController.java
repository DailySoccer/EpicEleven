package controllers;

import actions.AllowCors;
import com.mongodb.*;
import com.mongodb.util.JSON;
import model.Model;
import model.opta.*;
import org.bson.types.ObjectId;
import org.json.XML;
import play.libs.F;
import play.libs.WS;
import play.mvc.BodyParser;
import play.mvc.Controller;
import play.mvc.Result;
import utils.OptaUtils;


import java.io.UnsupportedEncodingException;
import java.util.*;

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
        Model.optaDB().insert(new OptaDB(bodyText,
                bodyAsJSON,
                name,
                request().headers(),
                startDate,
                System.currentTimeMillis()));
        /*
        if (bodyAsJSON.containsField("FANTASY")){
//            BasicDBList teams = (BasicDBList)bodyAsJSON.get("Team");
            processFantasy(bodyAsJSON);
        }
        */
        String feedType = null;
        if (request().headers().containsKey("X-Meta-Feed-Type")) {
            feedType = request().headers().get("X-Meta-Feed-Type")[0];
            OptaUtils.processOptaDBInput(feedType, bodyAsJSON);
        }
        return ok("Yeah, XML processed");
    }

    public static void childEvent(OptaEvent child, OptaEvent origin){
        child.optaEventId = new ObjectId();
        child.parentId = origin.eventId;

        child.gameId = origin.gameId;
        child.homeTeamId = origin.homeTeamId;
        child.awayTeamId = origin.awayTeamId;
        child.competitionId = origin.competitionId;
        child.seasonId = origin.seasonId;
        child.eventId = origin.eventId;
        child.typeId = origin.typeId;
        child.outcome = origin.outcome;
        child.timestamp = origin.timestamp;

        child.unixtimestamp = origin.unixtimestamp;
        child.lastModified = origin.lastModified;
        child.qualifiers = origin.qualifiers;
    }

    public static void derivateEvent(int eventType, String playerId, OptaEvent parentEvent){
        OptaEvent dbevent = Model.optaEvents().findOne("{parentId: #, gameId: #, typeId: #}", parentEvent.eventId,
                                                       parentEvent.gameId, eventType).as(OptaEvent.class);
        if (dbevent == null){
            dbevent = new OptaEvent();
            childEvent(dbevent, parentEvent);
            dbevent.optaPlayerId = playerId;
            Model.optaEvents().save(dbevent);
        }else if (dbevent.hasChanged(parentEvent)) {
            childEvent(dbevent, parentEvent);
            dbevent.optaPlayerId = playerId;
            Model.optaEvents().save(dbevent);
        }
    }

    /*
    private static void processFantasyPoints(OptaEvent event, ObjectId upsertedId){
        FantasyPoints dbFPoints = Model.fantasyPoints().findOne("{eventId: #}", upsertedId).as(FantasyPoints.class);
        FantasyPoints fpoints = new FantasyPoints();
        fpoints.eventType = event.typeId;
        fpoints.optaPlayerId = event.optaPlayerId;
        fpoints.eventId = event._id;
        fpoints.unixtimestamp = event.unixtimestamp;
        fpoints.timestamp = event.timestamp;

        Iterable<PointsTranslation> pointsTranslations = Model.pointsTranslation().
                find("{eventTypeId: #, unixtimestamp: {$lte: #}}",
                        fpoints.eventType, fpoints.unixtimestamp).sort("{unixtimestamp: -1}").as(PointsTranslation.class);

        PointsTranslation pointsTranslation = null;
        if (pointsTranslations.iterator().hasNext()){
            pointsTranslation = pointsTranslations.iterator().next();
            fpoints.pointsTranslationId = pointsTranslation._id;
            fpoints.points = pointsTranslation.points;
        }

        if (dbFPoints == null){
            Model.fantasyPoints().insert(fpoints);
        }else{

            Model.fantasyPoints().update("{eventId: #}", event._id).with(fpoints);
        }
    }
    */

    public static F.Promise<Result> importXML(){
        //F.Promise<Result> resultPromise = WS.url("http://dailysoccer.herokuapp.com/get_xml/").get().map(
        F.Promise<Result> resultPromise = WS.url("http://localhost:9000/get_xml").get().map(
                new F.Function<WS.Response, Result>(){
                    public Result apply(WS.Response response){
                        long startDate = System.currentTimeMillis();
                        String bodyText  = response.getBody();
                        String name = response.getHeader("x-default-filename");
                        BasicDBObject bodyAsJSON = (BasicDBObject) JSON.parse(XML.toJSONObject(bodyText).toString());
                        Model.optaDB().insert(new OptaDB(bodyText,
                                bodyAsJSON,
                                name,
                                null, //headers, cannot be obtained programatically
                                startDate,
                                System.currentTimeMillis()));

                        return ok("Imported");

                    }
                }
        );
       return resultPromise;
    }

    public static Result getXML(){
        //OptaDB someOptaData = Model.optaDB().findOne("{'Games': {$exists: true}}").as(OptaDB.class);
        OptaDB someOptaData = Model.optaDB().findOne().as(OptaDB.class);
        Map headers = someOptaData.headers;
        for (Object header: headers.keySet()){
            String value = ((String[]) headers.get(header))[0];
            response().setHeader(header.toString(), value);
        }
        response().setHeader("x-default-filename", someOptaData.name);
        response().setContentType("text/html");
        return ok(someOptaData.xml);
    }

    public static Result parseEvents(){
        //BasicDBObject game = Model.optaDB().findOne("{'Games': {$exists: true}}").as(BasicDBObject.class);
        Model.optaEvents().remove();
        Iterable<BasicDBObject> games = Model.optaDB().find("{'json.Games': {$exists: true}}").as(BasicDBObject.class);
        long initialTime = System.currentTimeMillis();
        int i=0;
        for (BasicDBObject object: games) {
            play.Logger.info("parse({}): {}", ++i, object.get("_id"));

            LinkedHashMap json = (LinkedHashMap) object.get("json");
            OptaUtils.processEvents((LinkedHashMap)json.get("Games"));
        }
        System.currentTimeMillis();
        return ok("Yeah, Game processed: "+(System.currentTimeMillis()-initialTime)+" milliseconds");
    }

    public static Result playersPoints(){
        Iterable<OptaPlayer> myPlayers = Model.optaPlayers().find().as(OptaPlayer.class);
        String allplayers = "<ul>";
        for (OptaPlayer myPlayer: myPlayers){
            Iterable<OptaEvent> optaEvents = Model.optaEvents().find("{optaPlayerId: #}", myPlayer.optaPlayerId).as(OptaEvent.class);
            int totalPoints = 0;
            for (OptaEvent optaEvent: optaEvents){
                totalPoints += optaEvent.points;
            }
            allplayers += "<li>"+myPlayer.name+"'s (<a href='/player/"+myPlayer.optaPlayerId+"'>"+myPlayer.optaPlayerId+"</a>) points: "+totalPoints+"</li>\n";
        }
        allplayers += "</ul>";
        return ok(allplayers).as("text/html");
    }

    public static Result playerPoints(int player){
        Iterable<OptaEvent> playerEvents = Model.optaEvents().find("{optaPlayerId: #}", player).as(OptaEvent.class);
        OptaPlayer myPlayer = Model.optaPlayers().findOne("{id: #}", player).as(OptaPlayer.class);
        int totalPoints = 0;
        LinkedHashMap events = new LinkedHashMap();
        LinkedHashMap points = new LinkedHashMap();

        for (OptaEvent playerEvent: playerEvents){
            totalPoints += playerEvent.points;
            if (playerEvent.points != 0){
                if (events.containsKey(playerEvent.typeId)){
                    events.put(playerEvent.typeId, 1+(int)events.get(playerEvent.typeId));
                }else{
                    events.put(playerEvent.typeId, 1);
                    points.put(playerEvent.typeId, playerEvent.points);
                }
            }
        }
        String html = "<h1>"+myPlayer.name+"'s points: "+totalPoints+"</h1> <ul>";
        for (Object eventType: events.keySet()){
            html += "<li>event "+eventType+"("+points.get(eventType)+" points): "+events.get(eventType)+" times</li>";
        }
        html += "</ul>";
        return ok(html).as("text/html");
//        return ok(my_player.firstname+" "+my_player.lastname+"'s points: "+totalPoints);
    }

    public static Result parseF1(){
        OptaDB f1 = Model.optaDB().findOne("{'headers.X-Meta-Feed-Type': 'F1'}").as(OptaDB.class);
        if (f1 != null)
            OptaUtils.processF1((BasicDBObject) f1.json);
        return ok("Yeah, F1 processed");
    }

    public static void processFantasy(BasicDBObject fantasy){
        ArrayList teams = new ArrayList();
        LinkedHashMap fantasy1 = (LinkedHashMap)fantasy.get("FANTASY");
        if (fantasy1.containsKey("Team")) {
            teams = (ArrayList)fantasy1.get("Team");
        }else{
            if (fantasy1.containsKey("Match")){
                LinkedHashMap match = (LinkedHashMap)(fantasy1.get("Match"));
                teams = (ArrayList)match.get("Team");
            }
            else{
                System.out.println("no match");
            }
        }

        for (Object team: teams){
            LinkedHashMap teamObject = (LinkedHashMap)team;
            ArrayList playersList = (ArrayList)teamObject.get("Player");
            OptaTeam myTeam = new OptaTeam();
            myTeam.optaTeamId = (String)teamObject.get("id");
            myTeam.name = (String)teamObject.get("name");
            myTeam.updatedTime = System.currentTimeMillis();
            Model.optaTeams().update("{optaTeamId: #}", myTeam.optaTeamId).upsert().with(myTeam);

            for (Object player: playersList){
                LinkedHashMap playerObject = (LinkedHashMap)player;
                int playerId = (int) playerObject.get("id");
                OptaPlayer myPlayer = OptaUtils.createPlayer(playerObject, teamObject);
                Model.optaPlayers().update("{optaTeamId: #}", playerId).upsert().with(myPlayer);
            }
        }
    }

    public static Result parseFantasy(){
        BasicDBObject fantasy = Model.optaDB().findOne("{'FANTASY': {$exists: true}}").as(BasicDBObject.class);
        //BasicDBObject game = Model.optaDB().findOne().as(BasicDBObject.class);
//        BasicDBList teams = (BasicDBList)game.get("Team");
        processFantasy(fantasy);
        return ok("Yeah, Fantasy processed");
    }

}
