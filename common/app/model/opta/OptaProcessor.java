package model.opta;

import com.mongodb.BasicDBObject;
import model.Model;
import model.PointsTranslation;
import org.bson.types.ObjectId;
import play.Logger;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Created by gnufede on 16/06/14.
 */
public class OptaProcessor {

    // Retorna los Ids de opta (gameIds, optaMachEventId) de los partidos que han cambiado
    public HashSet<String> processOptaDBInput(String feedType, BasicDBObject requestBody) {
    
        dirtyMatchEvents = new HashSet<>();

        if (feedType != null) {
            if (feedType.equals("F9")) {
                processF9(requestBody);
            } else if (feedType.equals("F24")) {
                processEvents(requestBody);
            } else if (feedType.equals("F1")) {
                processF1(requestBody);
            }
        }

        return dirtyMatchEvents;
    }

    private void processEvents(BasicDBObject gamesObj) {
        try {
            resetPointsTranslationCache();

            LinkedHashMap game = (LinkedHashMap) ((LinkedHashMap) gamesObj.get("Games")).get("Game");
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

    private void processEvent(LinkedHashMap event, LinkedHashMap game) {

        Date timestamp;
        if (event.containsKey("last_modified")) {
            timestamp = parseDate((String) event.get("last_modified"));
        } else {
            timestamp = parseDate((String) event.get("timestamp"));
        }

        HashMap<Integer, Date> eventsCache = getOptaEventsCache(game.get("id").toString());
        int eventId = (int) event.get("event_id");

        if (!eventsCache.containsKey(eventId) || timestamp.after(eventsCache.get(eventId))) {
            updateOrInsertEvent(event, game);
            eventsCache.put(eventId, timestamp);
        }
    }

    private void updateOrInsertEvent(LinkedHashMap event, LinkedHashMap game) {
        OptaEvent myEvent = new OptaEvent();
        myEvent.optaEventId = new ObjectId();
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
        // Asistencia
        if (myEvent.typeId==OptaEventType.PASS.code && myEvent.qualifiers.contains(210)){
            myEvent.typeId = OptaEventType.ASSIST.code;  //Asistencia -> 1210
        }
        // Falta/Penalty infligido
        else if (myEvent.typeId==OptaEventType.FOUL_RECEIVED.code && myEvent.outcome==0){
            if (myEvent.qualifiers.contains(9)){
                myEvent.typeId = OptaEventType.PENALTY_COMMITTED.code;  //Penalty infligido -> 1409
            } else {
                myEvent.typeId = OptaEventType.FOUL_COMMITTED.code;  // Falta infligida -> 1004
            }
        }
        // Tarjeta roja -> 1017
        else if (myEvent.typeId==OptaEventType.YELLOW_CARD.code && myEvent.qualifiers.contains(33)){
            myEvent.typeId = OptaEventType.RED_CARD.code;
        }
        // Penalty miss -> 1410
        else if ((myEvent.typeId==OptaEventType.MISS.code || myEvent.typeId==OptaEventType.POST.code ||
                  myEvent.typeId==OptaEventType.ATTEMPT_SAVED.code) &&
                myEvent.outcome==0 && myEvent.qualifiers.contains(9)){
            myEvent.typeId = OptaEventType.PENALTY_FAILED.code;
        }
        else if (myEvent.typeId==16 && myEvent.outcome==1) {
            // Gol en contra -> 1699
            if (myEvent.qualifiers.contains(28)) {
                myEvent.typeId = OptaEventType.OWN_GOAL.code;
            } else {
            // Diferencias en goles:
                try {
                    OptaPlayer scorer = Model.optaPlayers().findOne("{optaPlayerId: #}", "p"+myEvent.optaPlayerId).as(OptaPlayer.class);
                    if (scorer.position.equals("Goalkeeper")){
                        // Gol del portero
                        myEvent.typeId = OptaEventType.GOAL_SCORED_BY_GOALKEEPER.code;
                    } else if (scorer.position.equals("Defender")){
                        // Gol del defensa
                        myEvent.typeId = OptaEventType.GOAL_SCORED_BY_DEFENDER.code;
                    } else if (scorer.position.equals("Midfielder")){
                        // Gol del medio
                        myEvent.typeId = OptaEventType.GOAL_SCORED_BY_MIDFIELDER.code;
                    } else if (scorer.position.equals("Forward")){
                        // Gol del delantero
                        myEvent.typeId = OptaEventType.GOAL_SCORED_BY_FORWARD.code;
                    }
                } catch (NullPointerException e) {
                    Logger.error("Player not found: "+myEvent.optaPlayerId);
                }

            }
        }
        // Penalty parado -> 1058
        else if (myEvent.typeId==58 && !myEvent.qualifiers.contains(186)){
            myEvent.typeId = OptaEventType.GOALKEEPER_SAVES_PENALTY.code;
        }
        // Effective Tackle -> 1007
        else if (myEvent.typeId==OptaEventType.TACKLE.code && myEvent.outcome==1) {
            myEvent.typeId = OptaEventType.TACKLE_EFFECTIVE.code;
        }

        myEvent.points = getPoints(myEvent.typeId, myEvent.timestamp);
        myEvent.pointsTranslationId = pointsTranslationTableCache.get(myEvent.typeId);

        Model.optaEvents().update("{eventId: #, gameId: #}", myEvent.eventId, myEvent.gameId).upsert().with(myEvent);
        dirtyMatchEvents.add(myEvent.gameId);
    }


    public void recalculateAllEvents() {
        resetPointsTranslationCache();

        for (OptaEvent event : Model.optaEvents().find().as(OptaEvent.class)) {
            recalculateEvent(event);
        }
    }

    private void recalculateEvent(OptaEvent optaEvent) {
        optaEvent.points = getPoints(optaEvent.typeId, optaEvent.timestamp);
        optaEvent.pointsTranslationId = pointsTranslationTableCache.get(optaEvent.typeId);
        Model.optaEvents().update("{eventId: #, gameId: #}", optaEvent.eventId, optaEvent.gameId).upsert().with(optaEvent);
    }


    private int getPoints(int typeId, Date timestamp) {
        if (!pointsTranslationCache.containsKey(typeId)) {
            getPointsTranslation(typeId, timestamp);
        }
        return pointsTranslationCache.get(typeId);
    }

    private PointsTranslation getPointsTranslation(int typeId, Date timestamp){
        Iterable<PointsTranslation> pointsTranslations = Model.pointsTranslation().
                find("{eventTypeId: #, timestamp: {$lte: #}}", typeId, timestamp).sort("{timestamp: -1}").as(PointsTranslation.class);

        PointsTranslation pointsTranslation = null;
        if (pointsTranslations.iterator().hasNext()){
            pointsTranslation = pointsTranslations.iterator().next();
            pointsTranslationCache.put(typeId, pointsTranslation.points);
            pointsTranslationTableCache.put(typeId, pointsTranslation.pointsTranslationId);
        } else {
            pointsTranslationCache.put(typeId, 0);
            pointsTranslationTableCache.put(typeId, null);
        }
        return pointsTranslation;
    }

    private Date parseDate(String timestamp) {
        String dateConfig;
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

    private void processF1(BasicDBObject f1) {
        try {
            LinkedHashMap myF1 = (LinkedHashMap) ((LinkedHashMap) f1.get("SoccerFeed")).get("SoccerDocument");
            ArrayList matches = (ArrayList) myF1.get("MatchData");

            if (matches != null) {
                int competitionId = myF1.containsKey("competition_id")? (int) myF1.get("competition_id") : -1;

                for (Object match : matches) {
                    processMatchData((LinkedHashMap)match, competitionId, myF1);
                }
            }
        } catch (NullPointerException e){
            e.printStackTrace();
        }
    }

    private void processMatchData(LinkedHashMap matchObject, int competitionId, LinkedHashMap myF1) {

        HashMap<String, Date> optaMatchDatas = getOptaMatchDataCache(competitionId);
        String matchId = (String) matchObject.get("uID");
        Date timestamp = parseDate((String) matchObject.get("last_modified"));

        if (!optaMatchDatas.containsKey(matchId) || timestamp.after(optaMatchDatas.get(matchId))) {
            updateOrInsertMatchData(myF1, matchObject);
            //updateOrInsertMatchData(matchObject, competitionId);
            optaMatchDatas.put(matchId, timestamp);
        }
    }

    private String getStringValue(LinkedHashMap document, String key, String defaultValue){
        String value = defaultValue;
        if (document.get(key) instanceof String) {
            value = (String) document.get(key);
        } else {
            value = ((Integer) document.get(key)).toString();
        }
        return value;
    }

    private void updateOrInsertMatchData(LinkedHashMap myF1, LinkedHashMap matchObject) {

        LinkedHashMap matchInfo = (LinkedHashMap) matchObject.get("MatchInfo");

        OptaMatchEvent optaMatchEvent = new OptaMatchEvent();
        optaMatchEvent.optaMatchEventId = (String) matchObject.get("uID");
        optaMatchEvent.lastModified = parseDate((String) matchObject.get("last_modified"));
        optaMatchEvent.matchDate = parseDate((String) matchInfo.get("Date"));
        optaMatchEvent.competitionId = getStringValue(myF1, "competition_id", "NO COMPETITION ID");
        optaMatchEvent.seasonId = getStringValue(myF1, "season_id", "NO SEASON ID");

        optaMatchEvent.seasonName = myF1.containsKey("season_name")? (String) myF1.get("season_name"): "NO SEASON NAME";
        optaMatchEvent.competitionName = myF1.containsKey("competition_name")? (String) myF1.get("competition_name"): "NO COMPETITION NAME";
        optaMatchEvent.timeZone = (String) matchInfo.get("TZ");

        ArrayList teams = (ArrayList)matchObject.get("TeamData");
        if (teams != null) {
            for (Object team : teams) {
                if (((LinkedHashMap) team).get("Side").equals("Home")) {
                    optaMatchEvent.homeTeamId = (String) ((LinkedHashMap) team).get("TeamRef");
                } else {
                    optaMatchEvent.awayTeamId = (String) ((LinkedHashMap) team).get("TeamRef");
                }
            }
        }
        Model.optaMatchEvents().update("{optaMatchEventId: #}", optaMatchEvent.optaMatchEventId).upsert().with(optaMatchEvent);
    }

    private void processF9(BasicDBObject f9) {

        try {
            LinkedHashMap myF9 = (LinkedHashMap)((LinkedHashMap) f9.get("SoccerFeed")).get("SoccerDocument");

            if (myF9.get("Type").equals("Result")) {
                processFinishedMatch(myF9);
            }

            ArrayList teams = getTeamsFromF9(myF9);

            for (Object team : teams) {
                LinkedHashMap teamAsHashMap = (LinkedHashMap) team;

                OptaTeam myTeam = new OptaTeam();
                myTeam.optaTeamId = (String) teamAsHashMap.get("uID");
                myTeam.name = (String) teamAsHashMap.get("Name");
                myTeam.shortName = (String) teamAsHashMap.get("SYMID");
                myTeam.updatedTime = System.currentTimeMillis();

                ArrayList playersList = (ArrayList) teamAsHashMap.get("Player");

                if (playersList != null) { // Si no es un equipo placeholder
                    Model.optaTeams().update("{optaTeamId: #}", myTeam.optaTeamId).upsert().with(myTeam);

                    for (Object player : playersList) {
                        LinkedHashMap playerObject = (LinkedHashMap) player;
                        String playerId = (String) playerObject.get("uID");

                        // First search if player already exists:
                        if (playerId != null) {
                            OptaPlayer myPlayer = createPlayer(playerObject, teamAsHashMap);
                            Model.optaPlayers().update("{optaPlayerId: #}", playerId).upsert().with(myPlayer);
                        }
                    }
                }
            }
        } catch (NullPointerException e) {
            e.printStackTrace();
        }
    }

    private ArrayList getTeamsFromF9(LinkedHashMap myF9) {
        ArrayList teams = new ArrayList();

        if (myF9.containsKey("Team")) {
            teams = (ArrayList) myF9.get("Team");
        } else {
            if (myF9.containsKey("Match")) {
                teams = (ArrayList) ((LinkedHashMap) myF9.get("Match")).get("Team");
            } else {
                System.out.println("no match");
            }
        }
        return teams;
    }


    private void processFinishedMatch(LinkedHashMap F9) {
        String gameId = (String) F9.get("uID");

        ArrayList teamDatas = (ArrayList)((LinkedHashMap)F9.get("MatchData")).get("TeamData");

        for (Object teamData : teamDatas) {
            LinkedHashMap teamDataAsHashMap = ((LinkedHashMap) teamData);

            ArrayList teamStats = (ArrayList)teamDataAsHashMap.get("Stat");

            for (Object teamStat : teamStats) {
                LinkedHashMap teamStatAsHashMap = ((LinkedHashMap) teamStat);

                if (teamStatAsHashMap.get("Type").equals("goals_conceded")) {
                    if ((int) teamStatAsHashMap.get("content") == 0) {
                        processCleanSheet(F9, gameId, teamDataAsHashMap);
                    } else {
                        processGoalsAgainst(F9, gameId, teamDataAsHashMap);
                    }
                }
            }
        }

        dirtyMatchEvents.add(gameId);
    }

    private void processGoalsAgainst(LinkedHashMap F9, String gameId, LinkedHashMap teamData) {

        ArrayList matchPlayers = (ArrayList) ((LinkedHashMap) teamData.get("PlayerLineUp")).get("MatchPlayer");

        for (Object matchPlayer : matchPlayers) {
            LinkedHashMap matchPlayerAsHashMap = (LinkedHashMap)matchPlayer;

            if (matchPlayerAsHashMap.get("Position").equals("Goalkeeper") || matchPlayerAsHashMap.get("Position").equals("Defender")) {
                ArrayList stats = (ArrayList) matchPlayerAsHashMap.get("Stat");
                for (Object stat : stats) {
                    LinkedHashMap statAsHashMap = (LinkedHashMap)stat;

                    if (statAsHashMap.get("Type").equals("goals_conceded") && ((int) statAsHashMap.get("content") > 0)) {
                        createEvent(F9, gameId, matchPlayerAsHashMap, 2001, 20001, (int) statAsHashMap.get("content"));
                    }
                }
            }
        }
    }

    private void processCleanSheet(LinkedHashMap F9, String gameId, LinkedHashMap teamData) {

        ArrayList matchPlayers = (ArrayList) ((LinkedHashMap) teamData.get("PlayerLineUp")).get("MatchPlayer");

        for (Object matchPlayer : matchPlayers) {
            LinkedHashMap matchPlayerAsHashMap = (LinkedHashMap)matchPlayer;

            if (matchPlayerAsHashMap.get("Position").equals("Goalkeeper") || matchPlayerAsHashMap.get("Position").equals("Defender")) {
                ArrayList stats = (ArrayList) matchPlayerAsHashMap.get("Stat");
                for (Object stat : stats) {
                    LinkedHashMap statAsHashMap = (LinkedHashMap)stat;

                    if (statAsHashMap.get("Type").equals("mins_played") && ((int) statAsHashMap.get("content") > 59)) {
                        createEvent(F9, gameId, matchPlayerAsHashMap, 2000, 20000, 1);
                    }
                }
            }
        }
    }

    private void createEvent(LinkedHashMap F9, String gameId, LinkedHashMap matchPlayer, int typeId, int eventId, int times) {

        String playerId = (String) matchPlayer.get("PlayerRef");
        playerId = playerId.startsWith("p")? playerId.substring(1): playerId;

        String competitionId = (String)((LinkedHashMap)F9.get("Competition")).get("uID");
        competitionId = competitionId.startsWith("c")? competitionId.substring(1): competitionId;

        gameId = gameId.startsWith("f")? gameId.substring(1): gameId;

        Date timestamp = parseDate((String) ((LinkedHashMap) ((LinkedHashMap) F9.get("MatchData")).get("MatchInfo")).get("TimeStamp"));
        long unixtimestamp = timestamp.getTime();

        Model.optaEvents().remove("{typeId: #, eventId: #, optaPlayerId: #, gameId: #, competitionId: #}",
                typeId, eventId, playerId, gameId, competitionId);

        OptaEvent[] events = new OptaEvent[times];

        for (int i = 0; i < times; i++) {
            OptaEvent myEvent = new OptaEvent();
            myEvent.typeId = typeId;
            myEvent.eventId = eventId;
            myEvent.optaPlayerId = playerId;
            myEvent.gameId = gameId;
            myEvent.competitionId = competitionId;
            //TODO: Extraer SeasonID de Competition->Stat->Type==season_id->content
            myEvent.timestamp = timestamp;
            myEvent.unixtimestamp = unixtimestamp;
            myEvent.qualifiers = new ArrayList<>();
            myEvent.points = getPoints(myEvent.typeId, myEvent.timestamp);
            myEvent.pointsTranslationId = pointsTranslationTableCache.get(myEvent.typeId);

            events[i] = myEvent;
        }
        Model.optaEvents().insert((Object[]) events);
    }

    private OptaPlayer createPlayer(LinkedHashMap playerObject, LinkedHashMap teamObject){
        OptaPlayer myPlayer = new OptaPlayer();

        if (playerObject.containsKey("firstname")){
            myPlayer.optaPlayerId = (String) playerObject.get("id");
            myPlayer.firstname = (String) playerObject.get("firstname");
            myPlayer.lastname = (String) playerObject.get("lastname");
            myPlayer.position = (String) playerObject.get("position");
            myPlayer.teamId = (String) teamObject.get("id");
            myPlayer.teamName = (String) teamObject.get("name");
        }
        else {
            if (playerObject.containsKey("Name")){
                myPlayer.name = (String) playerObject.get("Name");
            } else if (playerObject.containsKey("PersonName")){
                if (((BasicDBObject)playerObject.get("PersonName")).containsKey("Known")) {
                    myPlayer.name = (String) ((BasicDBObject)playerObject.get("PersonName")).get("Known");
                } else {
                    myPlayer.name = (String) ((BasicDBObject)playerObject.get("PersonName")).get("First")+" "+
                            (String) ((BasicDBObject)playerObject.get("PersonName")).get("Last");
                }
            }
            if (playerObject.containsKey("uID")){
                myPlayer.optaPlayerId = (String) playerObject.get("uID");
            }
            if (playerObject.containsKey("Position")){
                myPlayer.position = (String) playerObject.get("Position");
            }
            myPlayer.teamId = (String) teamObject.get("uID");
            myPlayer.teamName = (String) teamObject.get("Name");
        }
        myPlayer.updatedTime = System.currentTimeMillis();
        return myPlayer;
    }

    private void resetPointsTranslationCache() {
        pointsTranslationCache = new HashMap<Integer, Integer>();
        pointsTranslationTableCache = new HashMap<Integer, ObjectId>();
    }

    private HashMap<Integer, Date> getOptaEventsCache(String gameId) {
        if (optaEventsCache == null) {
            optaEventsCache = new HashMap<String, HashMap<Integer, Date>>();
        }
        if (!optaEventsCache.containsKey(gameId)) {
            optaEventsCache.put(gameId, new HashMap<Integer, Date>());
        }
        return optaEventsCache.get(gameId);
    }

    private HashMap<String, Date> getOptaMatchDataCache(int competitionId) {
        if (optaMatchDataCache == null) {
            optaMatchDataCache = new HashMap<Integer, HashMap<String, Date>>();
        }
        if (!optaMatchDataCache.containsKey(competitionId)) {
            optaMatchDataCache.put(competitionId, new HashMap<String, Date>());
        }
        return optaMatchDataCache.get(competitionId);
    }

    private HashSet<String> dirtyMatchEvents;

    private HashMap<Integer, Integer> pointsTranslationCache;
    private HashMap<Integer, ObjectId> pointsTranslationTableCache;

    private HashMap<String, HashMap<Integer, Date>> optaEventsCache;
    private HashMap<Integer, HashMap<String, Date>> optaMatchDataCache;

}
