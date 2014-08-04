package model.opta;

import model.Model;
import model.PointsTranslation;
import org.bson.types.ObjectId;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.input.JDOMParseException;
import org.jdom2.input.SAXBuilder;
import play.Logger;

import java.io.StringReader;
import java.io.UnsupportedEncodingException;
import java.util.*;

/**
 * Created by gnufede on 16/06/14.
 */
public class OptaProcessor {

    // Retorna los Ids de opta (gameIds, optaMachEventId) de los partidos que han cambiado
    public HashSet<String> processOptaDBInput(String feedType, String requestBody) throws JDOMParseException {
        /*
        FIX para leer los XML chungos de la base de datos.
         */
        try {
            requestBody = new String (new String (new String (requestBody.getBytes("ISO-8859-1"), "UTF-8").
                                                                          getBytes("ISO-8859-1"), "UTF-8").
                                                                          getBytes("ISO-8859-1"), "UTF-8");

        } catch (UnsupportedEncodingException e) {
            Logger.error("WTF 59634", e);
        }

        try {
            SAXBuilder builder = new SAXBuilder();

            Document document = (Document) builder.build(new StringReader(requestBody));
            processOptaDBInput(feedType, document.getRootElement());

        } catch (JDOMParseException e) {
            throw e;
        }
        catch (Exception e) {
            Logger.error("WTF 95634", e);
        }


        return _dirtyMatchEvents;
    }

    private void processOptaDBInput(String feedType, Element requestBody) {
        _dirtyMatchEvents = new HashSet<>();

        if (feedType != null) {
            if (feedType.equals("F9")) {
                processF9(requestBody);
            } else if (feedType.equals("F24")) {
                processEvents(requestBody);
            } else if (feedType.equals("F1")) {
                processF1(requestBody);
            }
        }
    }

    private void processEvents(Element gamesObj) {

        resetPointsTranslationCache();

        Element game = gamesObj.getChild("Game");
        List<Element> events = game.getChildren("Event");
        for (Element event : events) {
            processEvent(event, game);
        }
    }

    private void processEvent(Element event, Element game) {

        Date timestamp = (event.getAttribute("last_modified") != null) ?
                             OptaEvent.parseDate(event.getAttributeValue("last_modified")):
                             OptaEvent.parseDate(event.getAttributeValue("timestamp"));

        HashMap<Integer, Date> eventsCache = getOptaEventsCache(game.getAttributeValue("id"));
        int eventId = (int) Integer.parseInt(event.getAttributeValue("id"));

        if (!eventsCache.containsKey(eventId) || timestamp.after(eventsCache.get(eventId))) {
            updateOrInsertEvent(event, game);
            eventsCache.put(eventId, timestamp);
        }
    }

    private void updateOrInsertEvent(Element event, Element game) {
        OptaEvent myEvent = new OptaEvent(event, game);
        myEvent.points = getPointsTranslation(myEvent.typeId, myEvent.timestamp);
        myEvent.pointsTranslationId = _pointsTranslationTableCache.get(myEvent.typeId);

        Model.optaEvents().update("{eventId: #, teamId: #, gameId: #}", myEvent.eventId, myEvent.teamId, myEvent.gameId).upsert().with(myEvent);

        _dirtyMatchEvents.add(myEvent.gameId);
    }


    public void recalculateAllEvents() {

        resetPointsTranslationCache();

        for (OptaEvent optaEvent : Model.optaEvents().find().as(OptaEvent.class)) {
            optaEvent.points = getPointsTranslation(optaEvent.typeId, optaEvent.timestamp);
            optaEvent.pointsTranslationId = _pointsTranslationTableCache.get(optaEvent.typeId);
            Model.optaEvents().update("{eventId: #, gameId: #, teamId: #}", optaEvent.eventId, optaEvent.gameId, optaEvent.teamId).upsert().with(optaEvent);
        }
    }

    private int getPointsTranslation(int typeId, Date timestamp){
        if (_pointsTranslationCache.containsKey(typeId)) {
            return _pointsTranslationCache.get(typeId);
        }
        Iterable<PointsTranslation> pointsTranslations = Model.pointsTranslation().
                                                         find("{eventTypeId: #, timestamp: {$lte: #}}", typeId, timestamp).
                                                         sort("{timestamp: -1}").as(PointsTranslation.class);

        PointsTranslation pointsTranslation = null;
        if (pointsTranslations.iterator().hasNext()){
            pointsTranslation = pointsTranslations.iterator().next();
            _pointsTranslationCache.put(typeId, pointsTranslation.points);
            _pointsTranslationTableCache.put(typeId, pointsTranslation.pointsTranslationId);
            return pointsTranslation.points;
        } else {
            _pointsTranslationCache.put(typeId, 0);
            _pointsTranslationTableCache.put(typeId, null);
            return 0;
        }
    }

    private void processF1(Element f1) {

        Element myF1 = f1.getChild("SoccerDocument");
        List<Element> matches = myF1.getChildren("MatchData");

        if (matches != null) {
            int competitionId = (myF1.getAttribute("competition_id")!=null)?
                                    (int) Integer.parseInt(myF1.getAttributeValue("competition_id")) : -1;

            for (Element match : matches) {
                processMatchData(match, competitionId, myF1);
            }
        }
    }

    private void processMatchData(Element matchObject, int competitionId, Element myF1) {

        HashMap<String, Date> optaMatchDatas = getOptaMatchDataCache(competitionId);
        String matchId = matchObject.getAttributeValue("uID");
        Date timestamp = OptaEvent.parseDate(matchObject.getAttributeValue("last_modified"));

        if (!optaMatchDatas.containsKey(matchId) || timestamp.after(optaMatchDatas.get(matchId))) {
            updateOrInsertMatchData(myF1, matchObject);
            optaMatchDatas.put(matchId, timestamp);
        }
    }

    private void updateOrInsertMatchData(Element myF1, Element matchObject) {
        Element matchInfo = matchObject.getChild("MatchInfo");

        OptaMatchEvent optaMatchEvent = new OptaMatchEvent();
        optaMatchEvent.optaMatchEventId = getStringId(matchObject, "uID", "_NO UID");
        if (matchObject.getAttribute("last_modified") != null) {
            optaMatchEvent.lastModified = OptaEvent.parseDate(matchObject.getAttributeValue("last_modified"));
        }
        optaMatchEvent.matchDate = OptaEvent.parseDate(matchInfo.getChild("Date").getContent().get(0).getValue());
        optaMatchEvent.competitionId = getStringId(myF1, "competition_id", "_NO COMPETITION ID");
        optaMatchEvent.seasonId = getStringId(myF1, "season_id", "_NO SEASON ID");

        optaMatchEvent.seasonName = (myF1.getAttribute("season_name")!=null)? myF1.getAttributeValue("season_name"): "NO SEASON NAME";
        optaMatchEvent.competitionName = (myF1.getAttribute("competition_name")!=null)? myF1.getAttributeValue("competition_name"): "NO COMPETITION NAME";
        optaMatchEvent.timeZone = matchInfo.getChild("TZ").getContent().get(0).getValue();

        List<Element> teams = matchObject.getChildren("TeamData");
        if (teams != null) {
            for (Element team : teams) {
                if (team.getAttributeValue("Side").equals("Home")) {
                    optaMatchEvent.homeTeamId = getStringId(team, "TeamRef", "_NO HOME TEAM ID");
                } else {
                    optaMatchEvent.awayTeamId =  getStringId(team, "TeamRef", "_NO AWAY TEAM ID");
                }
            }
        }
        Model.optaMatchEvents().update("{optaMatchEventId: #}", optaMatchEvent.optaMatchEventId).upsert().with(optaMatchEvent);
    }

    static public String getStringId(Element document, String key, String defaultValue){
        String value = getStringValue(document, key, defaultValue);
        return Character.isDigit(value.charAt(0))? value : value.substring(1);
    }

    static private String getStringValue(Element document, String key, String defaultValue){
        return (document.getAttribute(key)!=null)? document.getAttributeValue(key) : defaultValue ;
    }

    private void processF9(Element f9) {

        if (null == _pointsTranslationCache)
            resetPointsTranslationCache();

        Element myF9 = f9.getChild("SoccerDocument");

        if (myF9.getAttribute("Type").getValue().equals("Result")) {
            processFinishedMatch(myF9);
        }
        else if (myF9.getAttribute("Type").getValue().equals("STANDINGS Latest") ||
                 myF9.getAttribute("Type").getValue().equals("SQUADS Latest") ) {

            for (Element team : getTeamsFromF9(myF9)) {

                OptaTeam myTeam = new OptaTeam();
                myTeam.optaTeamId = getStringId(team, "uID", "_NO TEAM UID");
                myTeam.name = team.getChild("Name").getContent().get(0).getValue();// AttributeValue("Name");
                myTeam.updatedTime = new Date(System.currentTimeMillis());

                if (null != team.getChild("SYMID") && team.getChild("SYMID").getContentSize() > 0) {
                    myTeam.shortName = team.getChild("SYMID").getContent().get(0).getValue();//getAttributeValue("SYMID");
                }

                List<Element> playersList = team.getChildren("Player");

                if (playersList == null) // Si es un equipo placeholder nos lo saltamos
                    continue;

                Model.optaTeams().update("{optaTeamId: #}", myTeam.optaTeamId).upsert().with(myTeam);

                for (Element player : playersList) {
                    String playerId = getStringId(player, "uID", "_NO PLAYER UID");

                    // First search if player already exists:
                    if (playerId == null) // || playerObject.containsKey("PersonName"))
                        continue;

                    OptaPlayer myPlayer = createPlayer(player, team);

                    if (myPlayer != null)
                        Model.optaPlayers().update("{optaPlayerId: #}", playerId).upsert().with(myPlayer);
                }
            }
        }
    }

    private OptaPlayer createPlayer(Element playerObject, Element teamObject) {
        OptaPlayer myPlayer = new OptaPlayer();
            if (playerObject.getAttribute("firstname") != null){
                myPlayer.optaPlayerId = getStringId(playerObject, "id", "_NO PLAYER ID");
                myPlayer.firstname = playerObject.getAttributeValue("firstname");
                myPlayer.lastname = playerObject.getAttributeValue("lastname");
                myPlayer.name = myPlayer.firstname+" "+myPlayer.lastname;
                myPlayer.position = playerObject.getAttributeValue("position");
                myPlayer.teamId = getStringId(teamObject, "id", "_NO TEAM ID");
                myPlayer.teamName = teamObject.getAttributeValue("name");
            }
            else {
                if (playerObject.getAttribute("uID") != null){
                    myPlayer.optaPlayerId = getStringId(playerObject, "uID", "_NO PLAYER ID");
                }

                if (playerObject.getChild("Name") != null) {
                    myPlayer.name = playerObject.getChild("Name").getContent().get(0).getValue();
                    myPlayer.optaPlayerId = getStringId(playerObject, "uID", "_NO PLAYER ID");
                    myPlayer.position = playerObject.getChild("Position").getContent().get(0).getValue();
                } else if (playerObject.getChild("PersonName") != null) {
                    if (playerObject.getChild("PersonName").getChild("Known") != null) {
                        myPlayer.nickname = playerObject.getChild("PersonName").getChild("Known").getContent().get(0).getValue();
                    }
                    myPlayer.firstname = playerObject.getChild("PersonName").getChild("First").getContent().get(0).getValue();
                    myPlayer.lastname = playerObject.getChild("PersonName").getChild("Last").getContent().get(0).getValue();
                    myPlayer.name = myPlayer.firstname+" "+myPlayer.lastname;
                } else {
                    Logger.error("Not getting name for: "+myPlayer.optaPlayerId);
                }

                if (playerObject.getChild("Position") != null){
                    myPlayer.position = playerObject.getChild("Position").getContent().get(0).getValue();
                    if (myPlayer.position.equals("Substitute")) {
                        Logger.info("WTF 23344: Sustituto! {}", myPlayer.name );
                    }
                }

                myPlayer.teamId = getStringId(teamObject, "uID", "_NO TEAM ID");
                myPlayer.teamName = teamObject.getChild("Name").getContent().get(0).getValue();
            }
            myPlayer.updatedTime = new Date(System.currentTimeMillis());
            return myPlayer;
    }


    private List<Element> getTeamsFromF9(Element myF9) {
        List<Element> teams = new ArrayList<Element>();

        if (null != myF9.getChild("Team")) {
            teams = myF9.getChildren("Team");
        } else {
            if (null != myF9.getChild("Match")) {
                teams = myF9.getChild("Match").getChildren("Team");
            } else {
                Logger.info("WTF 34825: No match");
            }
        }
        return teams;
    }


    private void processFinishedMatch(Element F9) {
        String gameId = getStringId(F9, "uID", "_NO GAME ID");

        OptaMatchEventStats stats = new OptaMatchEventStats(gameId);

        List<Element> teamDatas = F9.getChild("MatchData").getChildren("TeamData");

        for (Element teamData : teamDatas) {
            List<Element> teamStats = teamData.getChildren("Stat");

            for (Element teamStat : teamStats) {
                if (teamStat.getAttribute("Type").getValue().equals("goals_conceded")) {
                    if ((int) Integer.parseInt(teamStat.getContent().get(0).getValue()) == 0) {
                        processCleanSheet(F9, gameId, teamData);
                    } else {
                        processGoalsAgainst(F9, gameId, teamData);
                    }
                }
            }

            stats.updateWithTeamData(teamData);
        }

        Model.optaMatchEventStats().update("{optaMatchEventId: #}", gameId).upsert().with(stats);

        _dirtyMatchEvents.add(gameId);
    }

    private void processGoalsAgainst(Element F9, String gameId, Element teamData) {
        List<Element> matchPlayers = teamData.getChild("PlayerLineUp").getChildren("MatchPlayer");
        int teamRef = (int) Integer.parseInt(getStringId(teamData,"TeamRef","999"));

        for (Element matchPlayer : matchPlayers) {
            if (matchPlayer.getAttribute("Position").getValue().equals("Goalkeeper") ||
                matchPlayer.getAttribute("Position").getValue().equals("Defender")) {
                List<Element> stats = matchPlayer.getChildren("Stat");
                for (Element stat : stats) {
                    if (stat.getAttribute("Type").getValue().equals("goals_conceded") &&
                        ((int) Integer.parseInt(stat.getContent().get(0).getValue()) > 0)) {
                        createEvent(F9, gameId, matchPlayer, teamRef, OptaEventType.GOAL_CONCEDED._code, 20001,
                                    (int) Integer.parseInt(stat.getContent().get(0).getValue()));
                    }
                }
            }
        }
    }


    private void processCleanSheet(Element F9, String gameId, Element teamData) {
        List<Element> matchPlayers = teamData.getChild("PlayerLineUp").getChildren("MatchPlayer");
        int teamRef = (int) Integer.parseInt(getStringId(teamData,"TeamRef","999"));

        for (Element matchPlayer : matchPlayers) {
            if (matchPlayer.getChild("Position").getValue().equals("Goalkeeper") ||
                matchPlayer.getChild("Position").getValue().equals("Defender")) {
                List<Element> stats = matchPlayer.getChildren("Stat");
                for (Element stat : stats) {
                    if (stat.getAttribute("Type").getValue().equals("mins_played") &&
                        ((int) Integer.parseInt(stat.getContent().get(0).getValue()) > 59)) {
                        createEvent(F9, gameId, matchPlayer, teamRef, OptaEventType.CLEAN_SHEET._code, 20000, 1);
                    }
                }
            }
        }

    }

    private void createEvent(Element F9, String gameId, Element matchPlayer, int teamId, int typeId, int eventId, int times) {
        String playerId = getStringId(matchPlayer, "PlayerRef", "_NO PLAYER ID");
        String competitionId = getStringId(F9.getChild("Competition"), "uID", "_NO COMPETITION UID");
        Date timestamp = OptaEvent.parseDate(F9.getChild("MatchData").getChild("MatchInfo").getAttributeValue("TimeStamp"));

        Model.optaEvents().remove("{typeId: #, eventId: #, optaPlayerId: #, teamId: #, gameId: #, competitionId: #}",
                typeId, eventId, playerId, teamId, gameId, competitionId);

        OptaEvent[] events = new OptaEvent[times];

        for (int i = 0; i < times; i++) {
            OptaEvent myEvent = new OptaEvent();
            myEvent.typeId = typeId;
            myEvent.eventId = eventId;
            myEvent.optaPlayerId = playerId;
            myEvent.teamId = teamId;
            myEvent.gameId = gameId;
            myEvent.competitionId = competitionId;
            //TODO: Extraer SeasonID de Competition->Stat->Type==season_id->content
            myEvent.timestamp = timestamp;
            myEvent.qualifiers = new ArrayList<>();
            myEvent.points = getPointsTranslation(myEvent.typeId, myEvent.timestamp);
            myEvent.pointsTranslationId = _pointsTranslationTableCache.get(myEvent.typeId);

            events[i] = myEvent;
        }
        Model.optaEvents().insert((Object[]) events);
    }

    private void resetPointsTranslationCache() {
        _pointsTranslationCache = new HashMap<Integer, Integer>();
        _pointsTranslationTableCache = new HashMap<Integer, ObjectId>();
    }

    private HashMap<Integer, Date> getOptaEventsCache(String gameId) {
        if (_optaEventsCache == null) {
            _optaEventsCache = new HashMap<String, HashMap<Integer, Date>>();
        }
        if (!_optaEventsCache.containsKey(gameId)) {
            _optaEventsCache.put(gameId, new HashMap<Integer, Date>());
        }
        return _optaEventsCache.get(gameId);
    }

    private HashMap<String, Date> getOptaMatchDataCache(int competitionId) {
        if (_optaMatchDataCache == null) {
            _optaMatchDataCache = new HashMap<Integer, HashMap<String, Date>>();
        }
        if (!_optaMatchDataCache.containsKey(competitionId)) {
            _optaMatchDataCache.put(competitionId, new HashMap<String, Date>());
        }
        return _optaMatchDataCache.get(competitionId);
    }

    private HashSet<String> _dirtyMatchEvents;

    private HashMap<Integer, Integer> _pointsTranslationCache;
    private HashMap<Integer, ObjectId> _pointsTranslationTableCache;

    private HashMap<String, HashMap<Integer, Date>> _optaEventsCache;
    private HashMap<Integer, HashMap<String, Date>> _optaMatchDataCache;

}