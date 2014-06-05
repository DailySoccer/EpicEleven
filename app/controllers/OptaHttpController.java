package controllers;

import com.google.common.collect.Iterables;
import com.mongodb.*;
import com.mongodb.util.JSON;
import model.FantasyPoints;
import model.Model;
import model.opta.*;
import org.bson.types.ObjectId;
import org.jongo.Find;
import org.json.XML;
import play.Logger;
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

/**
 * Created by gnufede on 30/05/14.
 */
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
        }catch (ClassCastException not_an_arraylist){
            LinkedHashMap event = (LinkedHashMap)game.get("Event");
            processEvent(event, game);
        }

    }

    public static void copyEvent(OptaEvent destination, OptaEvent origin){
        destination._id = new ObjectId();
        destination.parent_id = origin.event_id;

        destination.game_id = origin.game_id;
        destination.home_team_id = origin.home_team_id;
        destination.away_team_id = origin.away_team_id;
        destination.competition_id = origin.competition_id;
        destination.season_id = origin.season_id;
        destination.event_id = origin.event_id;
        destination.type_id = origin.type_id;
        destination.outcome = origin.outcome;
        destination.timestamp = origin.timestamp;

        destination.unixtimestamp = origin.unixtimestamp;
        destination.last_modified = origin.last_modified;
        destination.qualifiers = origin.qualifiers;
    }

    public static void generateEvent(int event_type, int player_id, OptaEvent parent_event){
        OptaEvent dbevent = Model.optaEvents().findOne("{parent_id: #, game_id: #, type_id: #}", parent_event.event_id,
                                                       parent_event.game_id, event_type).as(OptaEvent.class);

        if (dbevent == null){
            dbevent = new OptaEvent();
            copyEvent(dbevent, parent_event);
            dbevent.player_id = player_id;
            Model.optaEvents().save(dbevent);
        }else if (dbevent.hasChanged(parent_event)) {
            copyEvent(dbevent, parent_event);
            dbevent.player_id = player_id;
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
            PointsTranslation my_points_translation = new PointsTranslation();
            my_points_translation.event_code = pointsTable[i][0];
            PointsTranslation pointsTranslation = Model.pointsTranslation().findOne("{event_code: #}", my_points_translation.event_code).as(PointsTranslation.class);
            if (pointsTranslation == null){
                my_points_translation.unixtimestamp = 0L;
                my_points_translation.timestamp = new Date(my_points_translation.unixtimestamp);
            }
            else{
                my_points_translation.unixtimestamp = System.currentTimeMillis();
                my_points_translation.timestamp = new Date(my_points_translation.unixtimestamp);
            }
            my_points_translation.points = pointsTable[i][1];
            Model.pointsTranslation().insert(my_points_translation);
        }
        return ok();
    }

    private static void processFantasyPoints(OptaEvent event, ObjectId upsertedId){
        FantasyPoints dbFPoints = Model.fantasyPoints().findOne("{event_id: #}", upsertedId).as(FantasyPoints.class);
        FantasyPoints fpoints = new FantasyPoints();
        fpoints.event_type = event.type_id;
        fpoints.player_id = event.player_id;
        fpoints.event_id = event._id;
        fpoints.unixtimestamp = event.unixtimestamp;
        fpoints.timestamp = event.timestamp;

        Iterable<PointsTranslation> pointsTranslations = Model.pointsTranslation().
                find("{event_code: #, unixtimestamp: {$lte: #}}",
                        fpoints.event_type, fpoints.unixtimestamp).sort("{unixtimestamp: -1}").as(PointsTranslation.class);

        PointsTranslation pointsTranslation = null;
        if (pointsTranslations.iterator().hasNext()){
            pointsTranslation = pointsTranslations.iterator().next();
            fpoints.pointsTranslation_id = pointsTranslation._id;
            fpoints.points = pointsTranslation.points;
        }

        if (dbFPoints == null){
            Model.fantasyPoints().insert(fpoints);
        }else{

            Model.fantasyPoints().update("{event_id: #}", event._id).with(fpoints);
        }
    }

    private static void processEvent(LinkedHashMap event, LinkedHashMap game) {
        OptaEvent my_event = new OptaEvent();
        my_event._id = new ObjectId();
        my_event.game_id = (int) game.get("id");
        my_event.home_team_id = (int) game.get("home_team_id");
        my_event.away_team_id = (int) game.get("away_team_id");
        my_event.competition_id = (int) game.get("competition_id");
        my_event.season_id = (int) game.get("season_id");
        my_event.event_id = (int) event.get("event_id");
        my_event.type_id = (int) event.get("type_id");
        my_event.outcome = (int)event.get("outcome");
        my_event.timestamp = parseDate((String)event.get("timestamp"));

        my_event.unixtimestamp = my_event.timestamp.getTime();
        my_event.last_modified = parseDate((String) event.get("last_modified"));

        if (event.containsKey("player_id")){
            my_event.player_id = (int) event.get("player_id");
        }

        if (event.containsKey("Q")){
            try {
                ArrayList qualifier_list = (ArrayList) event.get("Q");
                my_event.qualifiers = new ArrayList<>(qualifier_list.size());
                for (Object qualifier: qualifier_list){
                    Integer temp_qualifier = (Integer)((LinkedHashMap)qualifier).get("qualifier_id");
                    my_event.qualifiers.add(temp_qualifier);
                }
            }catch (ClassCastException not_an_arraylist){
                LinkedHashMap qualifier_list = (LinkedHashMap) event.get("Q");
                my_event.qualifiers = new ArrayList<>(1);
                Integer temp_qualifier = (Integer)qualifier_list.get("qualifier_id");
                my_event.qualifiers.add(temp_qualifier);
            }
        }
        /*
        DERIVED EVENTS GO HERE
         */
        // Falta inflingida -> 1004
        if (my_event.type_id==4 && my_event.outcome==0){
            my_event.type_id = 1004;
        }
        // Tarjeta roja -> 1017
        if (my_event.type_id==17 && my_event.qualifiers.contains(33)){
            my_event.type_id = 1017;
        }
        /*
        // Gol al portero -> 1999
        if (my_event.type_id==16){
            //TODO: Extract current opposite goalkeeper
            generateEvent(1999, goalkeeper, my_event);
        }
        */




        OptaEvent dbevent = Model.optaEvents().findOne("{event_id: #, game_id: #}", my_event.event_id, my_event.game_id).as(OptaEvent.class);
        if (dbevent != null){
            boolean updated = dbevent.hasChanged(my_event);
            if (updated){
                WriteResult inserted = Model.optaEvents().update("{event_id: #, game_id: #}", my_event.event_id, my_event.game_id).with(my_event);
                processFantasyPoints(my_event, my_event._id);
            }
        }else {
            WriteResult inserted = Model.optaEvents().insert(my_event);
            processFantasyPoints(my_event, my_event._id);
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

    public static Result parseEvents(){
        //BasicDBObject game = Model.optaDB().findOne("{'Games': {$exists: true}}").as(BasicDBObject.class);
        Model.optaEvents().remove();
        Model.fantasyPoints().remove();
        BasicDBObject game = Model.optaDB().findOne("{'Games': {$exists: true}}").as(BasicDBObject.class);
        long initial_time = System.currentTimeMillis();
        processEvents(game);
        System.currentTimeMillis();
        return ok("Yeah, Game processed: "+(System.currentTimeMillis()-initial_time)+" milliseconds");
    }

    public static Result playersPoints(){
        Iterable<OptaPlayer> my_players = Model.optaPlayers().find().as(OptaPlayer.class);
        String allplayers = "<ul>";
        for (OptaPlayer my_player: my_players){
            Iterable<FantasyPoints> playerpoints = Model.fantasyPoints().find("{player_id: #}", my_player.id).as(FantasyPoints.class);
            int totalPoints = 0;
            for (FantasyPoints playerpoint: playerpoints){
                totalPoints += playerpoint.points;
            }
            allplayers += "<li>"+my_player.firstname+" "+my_player.lastname+"'s (<a href='/player/"+my_player.id+"'>"+my_player.id+"</a>) points: "+totalPoints+"</li>\n";
        }
        allplayers += "</ul>";
        return ok(allplayers).as("text/html");
    }

    public static Result playerPoints(int player){
        Iterable<FantasyPoints> playerpoints = Model.fantasyPoints().find("{player_id: #}", player).as(FantasyPoints.class);
        OptaPlayer my_player = Model.optaPlayers().findOne("{id: #}", player).as(OptaPlayer.class);
        int totalPoints = 0;
        LinkedHashMap events = new LinkedHashMap();
        LinkedHashMap points = new LinkedHashMap();

        for (FantasyPoints playerpoint: playerpoints){
            totalPoints += playerpoint.points;
            if (playerpoint.points != 0){
                if (events.containsKey(playerpoint.event_type)){
                    events.put(playerpoint.event_type, 1+(int)events.get(playerpoint.event_type));
                }else{
                    events.put(playerpoint.event_type, 1);
                    points.put(playerpoint.event_type, playerpoint.points);
                }
            }
        }
        String html = "<h1>"+my_player.firstname+" "+my_player.lastname+"'s points: "+totalPoints+"</h1> <ul>";
        for (Object event_type: events.keySet()){
            html += "<li>event "+event_type+"("+points.get(event_type)+" points): "+events.get(event_type)+" times</li>";
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
            OptaTeam my_team = new OptaTeam();
            my_team.id = (int)teamObject.get("id");
            my_team.name = (String)teamObject.get("name");
            my_team.updatedtime = System.currentTimeMillis();
            OptaTeam dbteam = Model.optaTeams().findOne("{id: #}", my_team.id).as(OptaTeam.class);
            if (dbteam != null){
                boolean updated = (dbteam.name != (String)teamObject.get("name"));
                if (updated){
                    Model.optaTeams().update("{id: #}", my_team.id).with(my_team);
                }
            }else{
                Model.optaTeams().insert(my_team, true);
            }

            for (Object player: playersList){
                LinkedHashMap playerObject = (LinkedHashMap)player;
                int player_id = (int) playerObject.get("id");
                // First search if player already exists:
                OptaPlayer dbplayer = Model.optaPlayers().findOne("{id: #}", player_id).as(OptaPlayer.class);
                if (dbplayer != null){
                    boolean updated = !((dbplayer.position == (String)playerObject.get("position")) &&
                                        (dbplayer.firstname == (String)playerObject.get("firstname")) &&
                                        (dbplayer.lastname == (String)playerObject.get("lastname")) &&
                                        (dbplayer.teamname == (String)teamObject.get("name")) &&
                                        (dbplayer.teamid == (int)teamObject.get("id")));
                    if (updated){
                        OptaPlayer my_player = createPlayer(playerObject, teamObject);
                        Model.optaPlayers().update("{id: #}", player_id).with(my_player);
                    }
                }else {
                    OptaPlayer my_player = createPlayer(playerObject, teamObject);
                    Model.optaPlayers().insert(my_player);
                }
            }
        }
    }

    public static OptaPlayer createPlayer(LinkedHashMap playerObject, LinkedHashMap teamObject){
        OptaPlayer my_player = new OptaPlayer();
        my_player.firstname = (String) playerObject.get("firstname");
        my_player.lastname = (String) playerObject.get("lastname");
        my_player.position = (String) playerObject.get("position");
        my_player.teamid = (int) teamObject.get("id");
        my_player.teamname = (String) teamObject.get("name");
        my_player.updatedtime = System.currentTimeMillis();
        return my_player;
    }

    public static Result parseFantasy(){
        BasicDBObject fantasy = Model.optaDB().findOne("{'FANTASY': {$exists: true}}").as(BasicDBObject.class);
        //BasicDBObject game = Model.optaDB().findOne().as(BasicDBObject.class);
//        BasicDBList teams = (BasicDBList)game.get("Team");
        processFantasy(fantasy);
        return ok("Yeah, Fantasy processed");
    }

}
