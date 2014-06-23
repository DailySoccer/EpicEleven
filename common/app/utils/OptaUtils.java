package utils;

import com.mongodb.BasicDBObject;
import model.Model;
import model.PointsTranslation;
import model.opta.OptaEvent;
import model.opta.OptaMatchEvent;
import model.opta.OptaPlayer;
import model.opta.OptaTeam;
import org.bson.types.ObjectId;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;

/**
 * Created by gnufede on 16/06/14.
 */
public class OptaUtils {

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
        try {
            LinkedHashMap games = (LinkedHashMap) gamesObj.get("Games");
            processEvents(games);
        } catch (NullPointerException e){
            e.printStackTrace();
        }


    }

    public static void processEvents(LinkedHashMap games){
        try {
            LinkedHashMap game = (LinkedHashMap) games.get("Game");

            Object events = game.get("Event");
            if (events instanceof ArrayList) {
                for (Object event : (ArrayList) events) {
                    processEvent((LinkedHashMap) event, game);
                }
            } else {
                processEvent((LinkedHashMap) events, game);
            }
        } catch (NullPointerException e){
            e.printStackTrace();
        }

    }

    private static void processEvent(LinkedHashMap event, LinkedHashMap game) {
        OptaEvent myEvent = new OptaEvent();
        myEvent.optaEventid = new ObjectId();
        myEvent.gameId = game.get("id").toString();
        myEvent.homeTeamId = game.get("home_team_id").toString();
        myEvent.awayTeamId = game.get("away_team_id").toString();
        myEvent.competitionId = game.get("competition_id").toString();
        myEvent.seasonId = game.get("season_id").toString();
        myEvent.periodId = (int) event.get("period_id");
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
        // Falta/Penalty infligido
        if (myEvent.typeId==4 && myEvent.outcome==0){
            if (myEvent.qualifiers.contains(9)){
                myEvent.typeId = 1409;  //Penalty infligido -> 1409
            } else {
                myEvent.typeId = 1004;  // Falta infligida -> 1004
            }
        }
        // Tarjeta roja -> 1017
        if (myEvent.typeId==17 && myEvent.qualifiers.contains(33)){
            myEvent.typeId = 1017;
        }
        // Penalty miss -> 1410
        if ((myEvent.typeId==13 || myEvent.typeId==14 || myEvent.typeId==15) &&
                myEvent.outcome==0 && myEvent.qualifiers.contains(9)){
            myEvent.typeId = 1410;
        }
        if (myEvent.typeId==16 && myEvent.outcome==1) {
            // Gol en contra -> 1699
            if (myEvent.qualifiers.contains(28)) {
                myEvent.typeId = 1699;
            } else {
            // Diferencias en goles:
                OptaPlayer scorer = Model.optaPlayers().findOne("{id: #}", "p"+myEvent.optaPlayerId).as(OptaPlayer.class);
                if (scorer.position == "Goalkeeper"){
                    // Gol del portero
                    myEvent.typeId = 1601;
                } else if (scorer.position == "Defender"){
                    // Gol del defensa
                    myEvent.typeId = 1602;
                } else if (scorer.position == "Midfielder"){
                    // Gol del medio
                    myEvent.typeId = 1603;
                } else if (scorer.position == "Forward"){
                    // Gol del delantero
                    myEvent.typeId = 1604;
                }

            }

        }
        // Gol al portero -> 1860
        if ((myEvent.typeId==10 || myEvent.typeId==11 || myEvent.typeId==12) &&
               myEvent.qualifiers.contains(186)){
            myEvent.typeId = 1860;
        }
        // Penalty parado -> 1058
        if (myEvent.typeId==58 && !myEvent.qualifiers.contains(186)){
            myEvent.typeId = 1058;
        }

        Iterable<PointsTranslation> pointsTranslations = Model.pointsTranslation().
                find("{eventTypeId: #, timestamp: {$lte: #}}",
                        myEvent.typeId, myEvent.timestamp).sort("{timestamp: -1}").as(PointsTranslation.class);

        PointsTranslation pointsTranslation = null;
        if (pointsTranslations.iterator().hasNext()){
            pointsTranslation = pointsTranslations.iterator().next();
            myEvent.pointsTranslationId = pointsTranslation._id;
            myEvent.points = pointsTranslation.points;
        }


        Model.optaEvents().update("{eventId: #, gameId: #}", myEvent.eventId, myEvent.gameId).upsert().with(myEvent);
    }

    private static Date parseDate(String timestamp) {
        String dateConfig = "";
        SimpleDateFormat dateFormat;
        if (timestamp.indexOf('-') > 0) {
            dateConfig = timestamp.indexOf('T') > 0 ? "yyyy-MM-dd'T'hh:mm:ss.SSSz" : "yyyy-MM-dd hh:mm:ss.SSSz";
            dateFormat = new SimpleDateFormat(dateConfig.substring(0, timestamp.length()));
        }else{
            dateConfig = timestamp.indexOf('T') > 0 ? "yyyyMMdd'T'hhmmssZ" : "yyyyMMdd hhmmssZ";
            dateFormat = new SimpleDateFormat(dateConfig);
        }
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

    public static void processF1(BasicDBObject f1) {
        try {
            LinkedHashMap myF1 = (LinkedHashMap) ((LinkedHashMap) f1.get("SoccerFeed")).get("SoccerDocument");

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
                    Model.optaMatchEvents().update("{id: #}", myOptaMatchEvent.id).upsert().with(myOptaMatchEvent);
                }

            }
        }catch (NullPointerException e){
            e.printStackTrace();
        }
    }

    public static void processF9(BasicDBObject f9){
        ArrayList teams = new ArrayList();
        try {
            LinkedHashMap myF9 = (LinkedHashMap) f9.get("SoccerFeed");
            myF9 = (LinkedHashMap) myF9.get("SoccerDocument");
            if (myF9.get("Type").equals("Result")){
                processFinishedMatch(myF9);
            }

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

            for (Object team : teams) {
                LinkedHashMap teamObject = (LinkedHashMap) team;
                ArrayList playersList = (ArrayList) teamObject.get("Player");
                OptaTeam myTeam = new OptaTeam();
                myTeam.id = (String) teamObject.get("uID");
                myTeam.name = (String) teamObject.get("Name");
                myTeam.shortName = (String) teamObject.get("SYMID");
                myTeam.updatedTime = System.currentTimeMillis();
                if (playersList != null) { //Si no es un equipo placeholder
                    Model.optaTeams().update("{id: #}", myTeam.id).upsert().with(myTeam);
                    for (Object player : playersList) {
                        LinkedHashMap playerObject = (LinkedHashMap) player;
                        String playerId = (String) playerObject.get("uID");
                        // First search if player already exists:
                        if (playerId != null) {
                            OptaPlayer myPlayer = createPlayer(playerObject, teamObject);
                            Model.optaPlayers().update("{id: #}", playerId).upsert().with(myPlayer);
                        }
                    }
                }
            }
        } catch (NullPointerException e) {
            e.printStackTrace();
        }
    }


    public static void processFinishedMatch(LinkedHashMap F9){
        String gameId = (String) F9.get("uID");
        ArrayList<LinkedHashMap> teamDatas =  (ArrayList<LinkedHashMap>)((LinkedHashMap)F9.get("MatchData")).get("TeamData");
        for (LinkedHashMap teamData: teamDatas){
            for (LinkedHashMap teamStat: (ArrayList<LinkedHashMap>) teamData.get("Stat")){
                if (teamStat.get("Type").equals("goals_conceded")) {
                    if ((int) teamStat.get("content") == 0) {
                        processCleanSheet(F9, gameId, teamData);
                    }else {
                        processGoalsAgainst(F9, gameId, teamData);
                    }
                }
            }
        }
    }

    public static void processGoalsAgainst(LinkedHashMap F9, String gameId, LinkedHashMap teamData) {
        ArrayList<LinkedHashMap> matchPlayers = (ArrayList) ((LinkedHashMap) teamData.get("PlayerLineUp")).
                                                                                      get("MatchPlayer");
        for (LinkedHashMap matchPlayer : matchPlayers) {
            if (matchPlayer.get("Position").equals("Defender")) {
                for (LinkedHashMap stat : (ArrayList<LinkedHashMap>) matchPlayer.get("Stat")) {
                    if (stat.get("Type").equals("goals_conceded") && ((int) stat.get("content") > 0)) {
                        createEvent(F9, gameId, matchPlayer, 2001, 20001);
                    }
                }
            }
        }
    }

    public static void processCleanSheet(LinkedHashMap F9, String gameId, LinkedHashMap teamData) {
        ArrayList<LinkedHashMap> matchPlayers = (ArrayList) ((LinkedHashMap) teamData.get("PlayerLineUp")).
                                                                                      get("MatchPlayer");
        for (LinkedHashMap matchPlayer : matchPlayers) {
            if (matchPlayer.get("Position").equals("Goalkeeper") || matchPlayer.get("Position").equals("Defender")) {
                for (LinkedHashMap stat : (ArrayList<LinkedHashMap>) matchPlayer.get("Stat")) {
                    if (stat.get("Type").equals("mins_played") && ((int) stat.get("content") > 59)) {
                        createEvent(F9, gameId, matchPlayer, 2000, 20000);
                    }
                }
            }
        }
    }

    public static void createEvent(LinkedHashMap F9, String gameId, LinkedHashMap matchPlayer, int typeId, int eventId) {
        String playerId = (String) matchPlayer.get("PlayerRef");
        OptaEvent myEvent = new OptaEvent();
        myEvent.typeId = typeId;
        myEvent.eventId = eventId;
        myEvent.optaPlayerId = playerId.startsWith("p")? playerId.substring(1): playerId;
        myEvent.gameId = gameId.startsWith("f")? gameId.substring(1): gameId;
        myEvent.timestamp = parseDate((String) ((LinkedHashMap) ((LinkedHashMap) F9.get("MatchData")).get("MatchInfo")).get("TimeStamp"));
        myEvent.unixtimestamp = myEvent.timestamp.getTime();
        myEvent.qualifiers = new ArrayList<>();
        Model.optaEvents().insert(myEvent);
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
}
