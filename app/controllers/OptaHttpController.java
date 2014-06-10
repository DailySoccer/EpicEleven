package controllers;

import actions.AllowCors;
import com.google.common.collect.Iterables;
import com.mongodb.*;
import com.mongodb.util.JSON;
import model.FantasyPoints;
import model.Model;
import model.opta.*;
import org.bson.types.ObjectId;
import org.jongo.Find;
import org.json.XML;
import org.w3c.dom.Document;
import play.Logger;
import play.libs.F;
import play.libs.WS;
import play.mvc.BodyParser;
import play.mvc.Controller;
import play.mvc.Result;


import play.libs.Json;
import com.fasterxml.jackson.databind.JsonNode;

import java.awt.*;
import java.lang.reflect.Array;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Created by gnufede on 30/05/14.
 */
@AllowCors.Origin
public class OptaHttpController extends Controller {
    @BodyParser.Of(value = BodyParser.TolerantText.class, maxLength = 4 * 1024 * 1024)
    public static Result optaXmlInput(){
        long startDate = System.currentTimeMillis();
        String bodyText = request().body().asText();
        BasicDBObject bodyAsJSON = (BasicDBObject) JSON.parse("{}");
        String name = "default-filename";
        try {
            name = request().headers().get("x-meta-default-filename")[0];
            bodyText = bodyText.substring(bodyText.indexOf('<'));
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

        if (bodyAsJSON.containsField("FANTASY")){
//            BasicDBList teams = (BasicDBList)bodyAsJSON.get("Team");
            processFantasy(bodyAsJSON);
        }

        if (bodyAsJSON.containsField("Games")){
            processEvents(bodyAsJSON);
        }
        return ok("Yeah, XML processed");
    }


    public static void processEvents(BasicDBObject gamesObj){
        LinkedHashMap games = (LinkedHashMap)gamesObj.get("Games");
        LinkedHashMap game = (LinkedHashMap)games.get("Game");

        try {
            ArrayList events = (ArrayList)game.get("Event");
            for (Object event: events){
                processEvent((LinkedHashMap)event, game);
            }
        }catch (ClassCastException notAnArraylist){
            LinkedHashMap event = (LinkedHashMap)game.get("Event");
            processEvent(event, game);
        }

    }

    public static void copyEvent(OptaEvent destination, OptaEvent origin){
        destination._id = new ObjectId();
        destination.parentId = origin.eventId;

        destination.gameId = origin.gameId;
        destination.homeTeamId = origin.homeTeamId;
        destination.awayTeamId = origin.awayTeamId;
        destination.competitionId = origin.competitionId;
        destination.seasonId = origin.seasonId;
        destination.eventId = origin.eventId;
        destination.typeId = origin.typeId;
        destination.outcome = origin.outcome;
        destination.timestamp = origin.timestamp;

        destination.unixtimestamp = origin.unixtimestamp;
        destination.lastModified = origin.lastModified;
        destination.qualifiers = origin.qualifiers;
    }

    public static void generateEvent(int eventType, int playerId, OptaEvent parentEvent){
        OptaEvent dbevent = Model.optaEvents().findOne("{parentId: #, gameId: #, typeId: #}", parentEvent.eventId,
                                                       parentEvent.gameId, eventType).as(OptaEvent.class);

        if (dbevent == null){
            dbevent = new OptaEvent();
            copyEvent(dbevent, parentEvent);
            dbevent.playerId = playerId;
            Model.optaEvents().save(dbevent);
        }else if (dbevent.hasChanged(parentEvent)) {
            copyEvent(dbevent, parentEvent);
            dbevent.playerId = playerId;
            Model.optaEvents().save(dbevent);
        }
    }

    public static Result createPointsTranslation(){
        int[][] pointsTable = {{1, 2},
                               {3, 10},
                               {4, 15},
                               {7, 15},
                               {8, 15},
                               {10, 20},
                               {11, 20},
                               {12, 10},
                               {13, 20},
                               {14, 20},
                               {15, 20},
                               {16, 100},
                               {17, -50},
                               {41, 10},
                               {50, -20},
                               {51, -20},
                               {72, -5},
                               {1004, -5},
                               {1017, -200}};
        for (int i = 0; i < pointsTable.length; i++){
            PointsTranslation myPointsTranslation = new PointsTranslation();
            myPointsTranslation.eventCode = pointsTable[i][0];
            PointsTranslation pointsTranslation = Model.pointsTranslation().findOne("{eventCode: #}", myPointsTranslation.eventCode).as(PointsTranslation.class);
            if (pointsTranslation == null){
                myPointsTranslation.unixtimestamp = 0L;
                myPointsTranslation.timestamp = new Date(myPointsTranslation.unixtimestamp);
            }
            else{
                myPointsTranslation.unixtimestamp = System.currentTimeMillis();
                myPointsTranslation.timestamp = new Date(myPointsTranslation.unixtimestamp);
            }
            myPointsTranslation.points = pointsTable[i][1];
            Model.pointsTranslation().insert(myPointsTranslation);
        }
        return ok();
    }

    private static void processFantasyPoints(OptaEvent event, ObjectId upsertedId){
        FantasyPoints dbFPoints = Model.fantasyPoints().findOne("{eventId: #}", upsertedId).as(FantasyPoints.class);
        FantasyPoints fpoints = new FantasyPoints();
        fpoints.eventType = event.typeId;
        fpoints.playerId = event.playerId;
        fpoints.eventId = event._id;
        fpoints.unixtimestamp = event.unixtimestamp;
        fpoints.timestamp = event.timestamp;

        Iterable<PointsTranslation> pointsTranslations = Model.pointsTranslation().
                find("{eventCode: #, unixtimestamp: {$lte: #}}",
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

    private static void processEvent(LinkedHashMap event, LinkedHashMap game) {
        OptaEvent myEvent = new OptaEvent();
        myEvent._id = new ObjectId();
        myEvent.gameId = (int) game.get("id");
        myEvent.homeTeamId = (int) game.get("home_team_id");
        myEvent.awayTeamId = (int) game.get("away_team_id");
        myEvent.competitionId = (int) game.get("competition_id");
        myEvent.seasonId = (int) game.get("season_id");
        myEvent.eventId = (int) event.get("event_id");
        myEvent.typeId = (int) event.get("type_id");
        myEvent.outcome = (int)event.get("outcome");
        myEvent.timestamp = parseDate((String)event.get("timestamp"));

        myEvent.unixtimestamp = myEvent.timestamp.getTime();
        myEvent.lastModified = parseDate((String) event.get("last_modified"));

        if (event.containsKey("player_id")){
            myEvent.playerId = (int) event.get("player_id");
        }

        if (event.containsKey("Q")){
            try {
                ArrayList qualifierList = (ArrayList) event.get("Q");
                myEvent.qualifiers = new ArrayList<>(qualifierList.size());
                for (Object qualifier: qualifierList){
                    Integer tempQualifier = (Integer)((LinkedHashMap)qualifier).get("qualifier_id");
                    myEvent.qualifiers.add(tempQualifier);
                }
            }catch (ClassCastException notAnArraylist){
                LinkedHashMap qualifierList = (LinkedHashMap) event.get("Q");
                myEvent.qualifiers = new ArrayList<>(1);
                Integer tempQualifier = (Integer)qualifierList.get("qualifier_id");
                myEvent.qualifiers.add(tempQualifier);
            }
        }
        /*
        DERIVED EVENTS GO HERE
         */
        // Falta inflingida -> 1004
        if (myEvent.typeId==4 && myEvent.outcome==0){
            myEvent.typeId = 1004;
        }
        // Tarjeta roja -> 1017
        if (myEvent.typeId==17 && myEvent.qualifiers.contains(33)){
            myEvent.typeId = 1017;
        }
        /*
        // Gol al portero -> 1999
        if (my_event.type_id==16){
            //TODO: Extract current opposite goalkeeper
            generateEvent(1999, goalkeeper, my_event);
        }
        */



        OptaEvent dbevent = Model.optaEvents().findOne("{eventId: #, gameId: #}", myEvent.eventId, myEvent.gameId).as(OptaEvent.class);
        if (dbevent != null){
            boolean updated = dbevent.hasChanged(myEvent);
            if (updated){
                WriteResult inserted = Model.optaEvents().update("{eventId: #, gameId: #}", myEvent.eventId, myEvent.gameId).with(myEvent);
                processFantasyPoints(myEvent, myEvent._id);
            }
        }else {
            WriteResult inserted = Model.optaEvents().insert(myEvent);
            processFantasyPoints(myEvent, myEvent._id);
        }
    }

    private static Date parseDate(String timestamp) {
        Date myDate = new Date();
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'hh:mm:ss.SSS");
        try {
            myDate = dateFormat.parse(timestamp);
        } catch (ParseException e) {
            try {
            dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'hh:mm:ss.SS");
            myDate = dateFormat.parse(timestamp);
            } catch (ParseException e1) {
                try {
                    dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'hh:mm:ss");
                    myDate = dateFormat.parse(timestamp);
                } catch (ParseException e2) {
                    e.printStackTrace();
                }
            }
        }
        return myDate;
    }

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
        Model.fantasyPoints().remove();
        BasicDBObject game = Model.optaDB().findOne("{'Games': {$exists: true}}").as(BasicDBObject.class);
        long initialTime = System.currentTimeMillis();
        processEvents(game);
        System.currentTimeMillis();
        return ok("Yeah, Game processed: "+(System.currentTimeMillis()-initialTime)+" milliseconds");
    }

    public static Result playersPoints(){
        Iterable<OptaPlayer> myPlayers = Model.optaPlayers().find().as(OptaPlayer.class);
        String allplayers = "<ul>";
        for (OptaPlayer myPlayer: myPlayers){
            Iterable<FantasyPoints> playerpoints = Model.fantasyPoints().find("{playerId: #}", myPlayer.id).as(FantasyPoints.class);
            int totalPoints = 0;
            for (FantasyPoints playerpoint: playerpoints){
                totalPoints += playerpoint.points;
            }
            allplayers += "<li>"+myPlayer.firstname+" "+myPlayer.lastname+"'s (<a href='/player/"+myPlayer.id+"'>"+myPlayer.id+"</a>) points: "+totalPoints+"</li>\n";
        }
        allplayers += "</ul>";
        return ok(allplayers).as("text/html");
    }

    public static Result playerPoints(int player){
        Iterable<FantasyPoints> playerpoints = Model.fantasyPoints().find("{playerId: #}", player).as(FantasyPoints.class);
        OptaPlayer myPlayer = Model.optaPlayers().findOne("{id: #}", player).as(OptaPlayer.class);
        int totalPoints = 0;
        LinkedHashMap events = new LinkedHashMap();
        LinkedHashMap points = new LinkedHashMap();

        for (FantasyPoints playerpoint: playerpoints){
            totalPoints += playerpoint.points;
            if (playerpoint.points != 0){
                if (events.containsKey(playerpoint.eventType)){
                    events.put(playerpoint.eventType, 1+(int)events.get(playerpoint.eventType));
                }else{
                    events.put(playerpoint.eventType, 1);
                    points.put(playerpoint.eventType, playerpoint.points);
                }
            }
        }
        String html = "<h1>"+myPlayer.firstname+" "+myPlayer.lastname+"'s points: "+totalPoints+"</h1> <ul>";
        for (Object eventType: events.keySet()){
            html += "<li>event "+eventType+"("+points.get(eventType)+" points): "+events.get(eventType)+" times</li>";
        }
        html += "</ul>";
        return ok(html).as("text/html");
//        return ok(my_player.firstname+" "+my_player.lastname+"'s points: "+totalPoints);
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
            myTeam.id = (int)teamObject.get("id");
            myTeam.name = (String)teamObject.get("name");
            myTeam.updatedTime = System.currentTimeMillis();
            OptaTeam dbteam = Model.optaTeams().findOne("{id: #}", myTeam.id).as(OptaTeam.class);
            if (dbteam != null){
                boolean updated = (dbteam.name != (String)teamObject.get("name"));
                if (updated){
                    Model.optaTeams().update("{id: #}", myTeam.id).with(myTeam);
                }
            }else{
                Model.optaTeams().insert(myTeam);
            }

            for (Object player: playersList){
                LinkedHashMap playerObject = (LinkedHashMap)player;
                int playerId = (int) playerObject.get("id");
                // First search if player already exists:
                OptaPlayer dbplayer = Model.optaPlayers().findOne("{id: #}", playerId).as(OptaPlayer.class);
                if (dbplayer != null){
                    boolean updated = !((dbplayer.position == (String)playerObject.get("position")) &&
                                        (dbplayer.firstname == (String)playerObject.get("firstname")) &&
                                        (dbplayer.lastname == (String)playerObject.get("lastname")) &&
                                        (dbplayer.teamName == (String)teamObject.get("name")) &&
                                        (dbplayer.teamId == (int)teamObject.get("id")));
                    if (updated){
                        OptaPlayer myPlayer = createPlayer(playerObject, teamObject);
                        Model.optaPlayers().update("{id: #}", playerId).with(myPlayer);
                    }
                }else {
                    OptaPlayer myPlayer = createPlayer(playerObject, teamObject);
                    Model.optaPlayers().insert(myPlayer);
                }
            }
        }
    }

    public static OptaPlayer createPlayer(LinkedHashMap playerObject, LinkedHashMap teamObject){
        OptaPlayer myPlayer = new OptaPlayer();
        myPlayer.firstname = (String) playerObject.get("firstname");
        myPlayer.lastname = (String) playerObject.get("lastname");
        myPlayer.position = (String) playerObject.get("position");
        myPlayer.teamId = (int) teamObject.get("id");
        myPlayer.teamName = (String) teamObject.get("name");
        myPlayer.updatedTime = System.currentTimeMillis();
        return myPlayer;
    }

    public static Result parseFantasy(){
        BasicDBObject fantasy = Model.optaDB().findOne("{'FANTASY': {$exists: true}}").as(BasicDBObject.class);
        //BasicDBObject game = Model.optaDB().findOne().as(BasicDBObject.class);
//        BasicDBList teams = (BasicDBList)game.get("Team");
        processFantasy(fantasy);
        return ok("Yeah, Fantasy processed");
    }

}
