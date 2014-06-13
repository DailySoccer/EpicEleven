package controllers;

import actions.AllowCors;
import com.mongodb.*;
import com.mongodb.util.JSON;
import model.Model;
import model.PointsTranslation;
import model.opta.*;
import org.bson.types.ObjectId;
import org.json.XML;
import play.libs.F;
import play.libs.WS;
import play.mvc.BodyParser;
import play.mvc.Controller;
import play.mvc.Result;


import java.io.UnsupportedEncodingException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
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
            processOptaDBInput(feedType, bodyAsJSON);
        }
        return ok("Yeah, XML processed");
    }


    public static void processOptaDBInput(String feedType, BasicDBObject requestBody){
        if (feedType.equals("F9")){
            processF9(requestBody);
        }
        else if (feedType.equals("F24")){
            processEvents(requestBody);
        }
        else if (feedType.equals("F1")){
            processF1(requestBody);
        }

    }

    public static void processEvents(BasicDBObject gamesObj){
        LinkedHashMap games = (LinkedHashMap)gamesObj.get("Games");
        LinkedHashMap game = (LinkedHashMap)games.get("Game");

        Object events = game.get("Event");
        if (events instanceof ArrayList) {
            for (Object event: (ArrayList)events){
                processEvent((LinkedHashMap)event, game);
            }
        } else {
            processEvent((LinkedHashMap)events, game);
        }

    }

    public static void childEvent(OptaEvent child, OptaEvent origin){
        child._id = new ObjectId();
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

    private static void processEvent(LinkedHashMap event, LinkedHashMap game) {
        OptaEvent myEvent = new OptaEvent();
        myEvent._id = new ObjectId();
        myEvent.gameId = game.get("id").toString();
        myEvent.homeTeamId = game.get("home_team_id").toString();
        myEvent.awayTeamId = game.get("away_team_id").toString();
        myEvent.competitionId = game.get("competition_id").toString();
        myEvent.seasonId = game.get("season_id").toString();
        myEvent.eventId = (int) event.get("event_id");
        myEvent.typeId = (int) event.get("type_id");
        myEvent.outcome = (int)event.get("outcome");
        myEvent.timestamp = parseDate((String)event.get("timestamp"));

        myEvent.unixtimestamp = myEvent.timestamp.getTime();
        myEvent.lastModified = parseDate((String) event.get("last_modified"));

        if (event.containsKey("player_id")){
            myEvent.optaPlayerId = event.get("player_id").toString();
        }

        if (event.containsKey("Q")){
            Object qualifierList = event.get("Q");
            if (qualifierList instanceof ArrayList) {
                myEvent.qualifiers = new ArrayList<>(((ArrayList) qualifierList).size());
                for (Object qualifier : (ArrayList) qualifierList) {
                    Integer tempQualifier = (Integer) ((LinkedHashMap) qualifier).get("qualifier_id");
                    myEvent.qualifiers.add(tempQualifier);
                }
            } else {
                myEvent.qualifiers = new ArrayList<>(1);
                Integer tempQualifier = (Integer)((LinkedHashMap)qualifierList).get("qualifier_id");
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
            derivateEvent(1999, goalkeeper, my_event);
        }
        */

        Iterable<PointsTranslation> pointsTranslations = Model.pointsTranslation().
                find("{eventTypeId: #, timestamp: {$lte: #}}",
                        myEvent.typeId, myEvent.timestamp).sort("{timestamp: -1}").as(PointsTranslation.class);

        PointsTranslation pointsTranslation = null;
        if (pointsTranslations.iterator().hasNext()){
            pointsTranslation = pointsTranslations.iterator().next();
            myEvent.pointsTranslationId = pointsTranslation._id;
            myEvent.points = pointsTranslation.points;
        }


        OptaEvent dbevent = Model.optaEvents().findOne("{eventId: #, gameId: #}", myEvent.eventId, myEvent.gameId).as(OptaEvent.class);
        if (dbevent != null){
            boolean updated = dbevent.hasChanged(myEvent);
            if (updated){
                WriteResult inserted = Model.optaEvents().update("{eventId: #, gameId: #}", myEvent.eventId, myEvent.gameId).with(myEvent);
                //processFantasyPoints(myEvent, myEvent._id);
            }
        }else {
            WriteResult inserted = Model.optaEvents().insert(myEvent);
            //processFantasyPoints(myEvent, myEvent._id);
        }
    }

    private static Date parseDate(String timestamp) {
        String dateConfig = timestamp.indexOf('T')>0? "yyyy-MM-dd'T'hh:mm:ss.SSSz" : "yyyy-MM-dd hh:mm:ss.SSSz";
        SimpleDateFormat dateFormat = new SimpleDateFormat(dateConfig.substring(0, timestamp.length()));
        int plusPos = timestamp.indexOf('+');
        if (plusPos>=19) {
            if (timestamp.substring(plusPos, timestamp.length()).equals("+00:00")) {
                timestamp = timestamp.substring(0, plusPos);
                dateFormat = new SimpleDateFormat(dateConfig.substring(0, timestamp.length()));
            } else {
                System.out.println(timestamp);
            }
        }

        Date myDate = null;
        try {
            myDate = dateFormat.parse(timestamp);
        } catch (ParseException e) {
            e.printStackTrace();
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
            Iterable<OptaEvent> optaEvents = Model.optaEvents().find("{optaPlayerId: #}", myPlayer.id).as(OptaEvent.class);
            int totalPoints = 0;
            for (OptaEvent optaEvent: optaEvents){
                totalPoints += optaEvent.points;
            }
            allplayers += "<li>"+myPlayer.name+"'s (<a href='/player/"+myPlayer.id+"'>"+myPlayer.id+"</a>) points: "+totalPoints+"</li>\n";
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
            processF1((BasicDBObject)f1.json);
        return ok("Yeah, F1 processed");
    }

    public static void processF1(BasicDBObject f1) {
        LinkedHashMap myF1 = (LinkedHashMap)((LinkedHashMap)f1.get("SoccerFeed")).get("SoccerDocument");

        int competitionId = myF1.containsKey("competition_id")? (int) myF1.get("competition_id"): -1;
        int seasonId = myF1.containsKey("season_id")? (int) myF1.get("season_id"): -1;
        String seasonName = myF1.containsKey("season_name")? (String) myF1.get("season_name"): "NO SEASON NAME";
        String competitionName = myF1.containsKey("competition_name")?
                (String) myF1.get("competition_name"): "NO COMPETITION NAME";


        ArrayList matches = myF1.containsKey("MatchData")? (ArrayList) myF1.get("MatchData"): null;
        if (matches != null){
            for (Object match: matches){
                OptaMatchEvent myOptaMatchEvent = new OptaMatchEvent();

                LinkedHashMap matchObject = (LinkedHashMap)match;
                LinkedHashMap matchInfo = (LinkedHashMap) matchObject.get("MatchInfo");
                myOptaMatchEvent.id = (String) matchObject.get("uID");
                myOptaMatchEvent.lastModified = parseDate((String) matchObject.get("last_modified"));
                myOptaMatchEvent.matchDate = parseDate((String) matchInfo.get("Date"));
                myOptaMatchEvent.competitionId = competitionId;
                myOptaMatchEvent.seasonId = seasonId;
                myOptaMatchEvent.seasonName = seasonName;
                myOptaMatchEvent.competitionName = competitionName;
                myOptaMatchEvent.timeZone = (String) matchInfo.get("TZ");
                ArrayList teams = matchObject.containsKey("TeamData")? (ArrayList)matchObject.get("TeamData"): null;
                if (teams != null)
                    for (Object team: teams){
                        if (((LinkedHashMap)team).get("Side").equals("Home")) {
                            myOptaMatchEvent.homeTeamId = (String) ((LinkedHashMap)team).get("TeamRef");
                        } else {
                            myOptaMatchEvent.awayTeamId = (String) ((LinkedHashMap)team).get("TeamRef");
                        }
                    }
                Model.optaMatchEvents().insert(myOptaMatchEvent);
            }

        }
    }

    public static void processF9(BasicDBObject f9){
        ArrayList teams = new ArrayList();
        LinkedHashMap myF9 = (LinkedHashMap)f9.get("SoccerFeed");
        myF9 = (LinkedHashMap)myF9.get("SoccerDocument");
        if (myF9.containsKey("Team")) {
            teams = (ArrayList) myF9.get("Team");
        } else {
            if (myF9.containsKey("Match")) { //TODO: Aserciones
                LinkedHashMap match = (LinkedHashMap) (myF9.get("Match"));
                teams = (ArrayList) match.get("Team");
            } else {
                System.out.println("no match");
            }
        }

        for (Object team: teams){
            LinkedHashMap teamObject = (LinkedHashMap)team;
            ArrayList playersList = (ArrayList)teamObject.get("Player");
            OptaTeam myTeam = new OptaTeam();
            myTeam.id = (String)teamObject.get("uID");
            myTeam.name = (String)teamObject.get("Name");
            myTeam.shortName = (String)teamObject.get("SYMID");
            myTeam.updatedTime = System.currentTimeMillis();
            OptaTeam dbteam = Model.optaTeams().findOne("{id: #}", myTeam.id).as(OptaTeam.class);
            if (playersList != null) { //Si no es un equipo placeholder
                if (dbteam != null) {
                    if (dbteam.name != (String)teamObject.get("name")){
                        Model.optaTeams().update("{id: #}", myTeam.id).with(myTeam); //TODO: meter upsert antes de with
                    }
                } else {
                    Model.optaTeams().insert(myTeam);
                }

                for (Object player: playersList) {
                    LinkedHashMap playerObject = (LinkedHashMap) player;
                    String playerId = (String) playerObject.get("uID");
                    // First search if player already exists:
                    if (playerId != null){
                        OptaPlayer dbplayer = Model.optaPlayers().findOne("{id: #}", playerId).as(OptaPlayer.class);
                        if (dbplayer != null) {
                            if (!((dbplayer.position == (String) playerObject.get("Position")) &&
                                    (dbplayer.name == (String) playerObject.get("Name")) &&
                                    (dbplayer.teamName == (String) teamObject.get("Name")) &&
                                    (dbplayer.teamId == (String) teamObject.get("uID")))) {
                                OptaPlayer myPlayer = createPlayer(playerObject, teamObject);
                                Model.optaPlayers().update("{id: #}", playerId).with(myPlayer);
                            }
                        } else {
                            OptaPlayer myPlayer = createPlayer(playerObject, teamObject);
                            Model.optaPlayers().insert(myPlayer);
                        }
                    }
                }
            }
        }
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
            myTeam.id = (String)teamObject.get("id");
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
                                        (dbplayer.teamId == (String)teamObject.get("id")));
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

        if (playerObject.containsKey("firstname")){
            myPlayer.id = (String) playerObject.get("id");
            myPlayer.firstname = (String) playerObject.get("firstname");
            myPlayer.lastname = (String) playerObject.get("lastname");
            myPlayer.position = (String) playerObject.get("position");
            myPlayer.teamId = (String) teamObject.get("id");
            myPlayer.teamName = (String) teamObject.get("name");
        }else if (playerObject.containsKey("Name")){
            myPlayer.id = (String) playerObject.get("uID");
            myPlayer.name = (String) playerObject.get("Name");
            myPlayer.position = (String) playerObject.get("Position");
            myPlayer.teamId = (String) teamObject.get("uID");
            myPlayer.teamName = (String) teamObject.get("Name");
        }
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
