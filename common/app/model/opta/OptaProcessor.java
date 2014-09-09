package model.opta;

import model.GlobalDate;
import model.Model;
import model.PointsTranslation;
import org.bson.types.ObjectId;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.input.SAXBuilder;
import play.Logger;

import java.io.StringReader;
import java.util.*;

public class OptaProcessor {

    static public boolean isDocumentValidForProcessing(String feedType, String competitionId) {
        boolean valid = false;

        if (feedType.equals("F9") || feedType.equals("F24") || feedType.equals("F1")) {
            OptaCompetition optaCompetition = OptaCompetition.findOne(competitionId);
            valid = (optaCompetition != null) && optaCompetition.activated;
        }
        else
        if (feedType.equals("F40")) {
            // El filtro no podemos aplicarlo cuando en los documentos "F40"
            //   se procesa una nueva competición o es una competición que está activa
            OptaCompetition optaCompetition = OptaCompetition.findOne(competitionId);
            valid = (optaCompetition == null) || optaCompetition.activated;
        }

        return valid;
    }

    // Retorna los Ids de opta (gameIds, optaMachEventId) de los partidos que han cambiado
    public HashSet<String> processOptaDBInput(String feedType, String fileName, String requestBody) {
        _dirtyMatchEvents = new HashSet<>();

        try {
            Element requestBodyElement = new SAXBuilder().build(new StringReader(requestBody)).getRootElement();

            if (feedType.equals("F9")) {
                processF9(requestBodyElement);
            }
            else if (feedType.equals("F40")) {
                processF40(requestBodyElement);
            }
            else if (feedType.equals("F24")) {
                processEvents(requestBodyElement);
            }
            else if (feedType.equals("F1")) {
                processF1(requestBodyElement);
            }
        }
        catch (Exception e) {
            Logger.error("WTF 6312", e);
        }

        return _dirtyMatchEvents;
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
                          GlobalDate.parseDate(event.getAttributeValue("last_modified"), null):
                          GlobalDate.parseDate(event.getAttributeValue("timestamp"), null);

        HashMap<Integer, Date> eventsCache = getOptaEventsCache(game.getAttributeValue("id"));
        int eventId = Integer.parseInt(event.getAttributeValue("id"));

        if (!eventsCache.containsKey(eventId) || timestamp.after(eventsCache.get(eventId))) {
            updateOrInsertEvent(event, game);
            eventsCache.put(eventId, timestamp);
        }
    }

    private void updateOrInsertEvent(Element event, Element game) {
        OptaEvent optaEvent = new OptaEvent(event, game);
        updateEvent(optaEvent);

        _dirtyMatchEvents.add(optaEvent.gameId);
    }

    private void updateEvent(OptaEvent optaEvent) {
        optaEvent.points = getPointsTranslation(optaEvent.typeId, optaEvent.timestamp);
        optaEvent.pointsTranslationId = _pointsTranslationTableCache.get(optaEvent.typeId);
        Model.optaEvents().update("{eventId: #, teamId: #, gameId: #}", optaEvent.eventId, optaEvent.teamId, optaEvent.gameId).upsert().with(optaEvent);
    }

    public void recalculateAllEvents() {
        resetPointsTranslationCache();

        for (OptaEvent optaEvent : Model.optaEvents().find().as(OptaEvent.class)) {
            updateEvent(optaEvent);
        }
    }

    private int getPointsTranslation(int typeId, Date timestamp){
        if (_pointsTranslationCache.containsKey(typeId)) {
            return _pointsTranslationCache.get(typeId);
        }
        Iterable<PointsTranslation> pointsTranslations = Model.pointsTranslation().
                                                         find("{eventTypeId: #, timestamp: {$lte: #}}", typeId, timestamp).
                                                         sort("{timestamp: -1}").as(PointsTranslation.class);

        if (pointsTranslations.iterator().hasNext()){
            PointsTranslation pointsTranslation = pointsTranslations.iterator().next();
            _pointsTranslationCache.put(typeId, pointsTranslation.points);
            _pointsTranslationTableCache.put(typeId, pointsTranslation.pointsTranslationId);
            return pointsTranslation.points;
        }
        else {
            _pointsTranslationCache.put(typeId, 0);
            _pointsTranslationTableCache.put(typeId, null);
            return 0;
        }
    }

    private void processF1(Element f1) {

        Element myF1 = f1.getChild("SoccerDocument");
        List<Element> matches = myF1.getChildren("MatchData");

        if (matches != null) {
            int competitionId = (myF1.getAttribute("competition_id")!=null)? Integer.parseInt(myF1.getAttributeValue("competition_id")) : -1;

            for (Element match : matches) {
                processMatchData(match, competitionId, myF1);
            }
        }
    }

    private void processMatchData(Element matchObject, int competitionId, Element myF1) {

        HashMap<String, Date> optaMatchDatas = getOptaMatchDataCache(competitionId);
        String matchId = matchObject.getAttributeValue("uID");
        Date timestamp = GlobalDate.parseDate(matchObject.getAttributeValue("last_modified"), null);

        if (!optaMatchDatas.containsKey(matchId) || timestamp.after(optaMatchDatas.get(matchId))) {
            updateOrInsertMatchData(myF1, matchObject);
            optaMatchDatas.put(matchId, timestamp);
        }
    }

    private void updateOrInsertMatchData(Element myF1, Element matchObject) {
        Element matchInfo = matchObject.getChild("MatchInfo");

        OptaMatchEvent optaMatchEvent = new OptaMatchEvent();
        optaMatchEvent.optaMatchEventId = getStringId(matchObject, "uID");

        if (matchObject.getAttribute("last_modified") != null) {
            optaMatchEvent.lastModified = GlobalDate.parseDate(matchObject.getAttributeValue("last_modified"), null);
        }

        optaMatchEvent.matchDate = GlobalDate.parseDate(matchInfo.getChild("Date").getContent().get(0).getValue(),
                                                        matchInfo.getChild("TZ").getContent().get(0).getValue());
        optaMatchEvent.competitionId = getStringId(myF1, "competition_id");
        optaMatchEvent.seasonId = getStringId(myF1, "season_id");

        optaMatchEvent.seasonName = (myF1.getAttribute("season_name")!=null)? myF1.getAttributeValue("season_name"): "NO SEASON NAME";
        optaMatchEvent.competitionName = (myF1.getAttribute("competition_name")!=null)? myF1.getAttributeValue("competition_name"): "NO COMPETITION NAME";

        List<Element> teams = matchObject.getChildren("TeamData");
        if (teams != null) {
            for (Element team : teams) {
                if (team.getAttributeValue("Side").equals("Home")) {
                    optaMatchEvent.homeTeamId = getStringId(team, "TeamRef");
                } else {
                    optaMatchEvent.awayTeamId =  getStringId(team, "TeamRef");
                }
            }
        }

        Model.optaMatchEvents().update("{optaMatchEventId: #}", optaMatchEvent.optaMatchEventId).upsert().with(optaMatchEvent);
    }

    static public String getStringId(Element document, String key){

        if (document.getAttribute(key) == null) {
            throw new RuntimeException("WTF 2906: " + key);
        }

        String value = document.getAttributeValue(key);
        return Character.isDigit(value.charAt(0))? value : value.substring(1);
    }

    private void processF9(Element f9) {

        if (null == _pointsTranslationCache)
            resetPointsTranslationCache();

        // Obtener las estadísticas (minutos jugados por los futbolistas) y eventos (cleanSheet, goalsAgainst)
        Element myF9 = f9.getChild("SoccerDocument");

        if (myF9.getAttribute("Type").getValue().equals("Result")) {
            processFinishedMatch(myF9);
        }
    }

    private void processF40(Element f40) {

        if (null == _pointsTranslationCache)
            resetPointsTranslationCache();

        // Obtener la lista de teams y players
        Element myF40 = f40.getChild("SoccerDocument");

        if (!myF40.getAttribute("Type").getValue().equals("SQUADS Latest"))
            throw new RuntimeException("WTF 7349: processF40");

        String competitionId = myF40.getAttribute("competition_id").getValue();

        if (OptaCompetition.findOne(competitionId) == null) {
            Model.optaCompetitions().insert(new OptaCompetition(competitionId,
                                                                myF40.getAttribute("competition_code").getValue(),
                                                                myF40.getAttribute("competition_name").getValue()));
        }

        for (Element team : myF40.getChildren("Team")) {

            List<Element> playersList = team.getChildren("Player");

            if (playersList == null) // Si es un equipo placeholder nos lo saltamos
                continue;

            OptaTeam myTeam = new OptaTeam();
            myTeam.optaTeamId = getStringId(team, "uID");
            myTeam.name = team.getChild("Name").getContent().get(0).getValue();// AttributeValue("Name");
            myTeam.updatedTime = GlobalDate.getCurrentDate();

            if (null != team.getChild("SYMID") && team.getChild("SYMID").getContentSize() > 0) {
                myTeam.shortName = team.getChild("SYMID").getContent().get(0).getValue();//getAttributeValue("SYMID");
            }

            Model.optaTeams()
                    .update("{optaTeamId: #}", myTeam.optaTeamId)
                    .upsert()
                    .with("{$set: {optaTeamId:#, name:#, shortName:#, updatedTime:#, dirty:#}, $addToSet: {competitionIds:#}}",
                            myTeam.optaTeamId, myTeam.name, myTeam.shortName, myTeam.updatedTime, myTeam.dirty, competitionId);

            for (Element player : playersList) {
                String playerId = getStringId(player, "uID");

                OptaPlayer myPlayer = createPlayer(player, team);
                Model.optaPlayers().update("{optaPlayerId: #}", playerId).upsert().with(myPlayer);
            }
        }
    }

    private OptaPlayer createPlayer(Element playerObject, Element teamObject) {
        OptaPlayer myPlayer = new OptaPlayer();

        if (playerObject.getAttribute("firstname") != null) {
            myPlayer.optaPlayerId = getStringId(playerObject, "id");
            myPlayer.firstname = playerObject.getAttributeValue("firstname");
            myPlayer.lastname = playerObject.getAttributeValue("lastname");
            myPlayer.name = myPlayer.firstname + " " + myPlayer.lastname;
            myPlayer.position = playerObject.getAttributeValue("position");
            myPlayer.teamId = getStringId(teamObject, "id");
            myPlayer.teamName = teamObject.getAttributeValue("name");
        }
        else {
            if (playerObject.getAttribute("uID") != null) {
                myPlayer.optaPlayerId = getStringId(playerObject, "uID");
            }

            if (playerObject.getChild("Name") != null) {
                myPlayer.name = playerObject.getChild("Name").getContent().get(0).getValue();
                myPlayer.optaPlayerId = getStringId(playerObject, "uID");
                myPlayer.position = playerObject.getChild("Position").getContent().get(0).getValue();
            }
            else if (playerObject.getChild("PersonName") != null) {
                if (playerObject.getChild("PersonName").getChild("Known") != null) {
                    myPlayer.nickname = playerObject.getChild("PersonName").getChild("Known").getContent().get(0).getValue();
                }
                myPlayer.firstname = playerObject.getChild("PersonName").getChild("First").getContent().get(0).getValue();
                myPlayer.lastname = playerObject.getChild("PersonName").getChild("Last").getContent().get(0).getValue();
                myPlayer.name = myPlayer.firstname + " " + myPlayer.lastname;
            }
            else {
                Logger.error("WTF 29211: No name for optaPlayerId " + myPlayer.optaPlayerId);
            }

            if (playerObject.getChild("Position") != null){
                myPlayer.position = playerObject.getChild("Position").getContent().get(0).getValue();
                if (myPlayer.position.equals("Substitute")) {
                    Logger.info("WTF 23344: Sustituto! {}", myPlayer.name);
                }
            }

            myPlayer.teamId = getStringId(teamObject, "uID");
            myPlayer.teamName = teamObject.getChild("Name").getContent().get(0).getValue();
        }

        myPlayer.updatedTime = GlobalDate.getCurrentDate();

        return myPlayer;
    }

    private void processFinishedMatch(Element F9) {
        String gameId = getStringId(F9, "uID");

        OptaMatchEventStats stats = new OptaMatchEventStats(gameId);

        List<Element> teamDatas = F9.getChild("MatchData").getChildren("TeamData");

        for (Element teamData : teamDatas) {
            List<Element> teamStats = teamData.getChildren("Stat");

            for (Element teamStat : teamStats) {
                if (teamStat.getAttribute("Type").getValue().equals("goals_conceded")) {

                    if (Integer.parseInt(teamStat.getContent().get(0).getValue()) == 0) {
                        processCleanSheet(F9, gameId, teamData);
                    }
                    else {
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
        int teamRef = Integer.parseInt(getStringId(teamData,"TeamRef"));

        for (Element matchPlayer : matchPlayers) {
            if (matchPlayer.getAttribute("Position").getValue().equals("Goalkeeper") ||
                matchPlayer.getAttribute("Position").getValue().equals("Defender")) {
                List<Element> stats = matchPlayer.getChildren("Stat");
                for (Element stat : stats) {
                    if (stat.getAttribute("Type").getValue().equals("goals_conceded") &&
                        (Integer.parseInt(stat.getContent().get(0).getValue()) > 0)) {
                        createEvent(F9, gameId, matchPlayer, teamRef, OptaEventType.GOAL_CONCEDED.code, 20001,
                                    Integer.parseInt(stat.getContent().get(0).getValue()));
                    }
                }
            }
        }
    }


    private void processCleanSheet(Element F9, String gameId, Element teamData) {
        List<Element> matchPlayers = teamData.getChild("PlayerLineUp").getChildren("MatchPlayer");
        int teamRef = Integer.parseInt(getStringId(teamData,"TeamRef"));

        for (Element matchPlayer : matchPlayers) {
            if (matchPlayer.getChild("Position").getValue().equals("Goalkeeper") ||
                matchPlayer.getChild("Position").getValue().equals("Defender")) {
                List<Element> stats = matchPlayer.getChildren("Stat");
                for (Element stat : stats) {
                    if (stat.getAttribute("Type").getValue().equals("mins_played") &&
                        (Integer.parseInt(stat.getContent().get(0).getValue()) > 59)) {
                        createEvent(F9, gameId, matchPlayer, teamRef, OptaEventType.CLEAN_SHEET.code, 20000, 1);
                    }
                }
            }
        }

    }

    private void createEvent(Element F9, String gameId, Element matchPlayer, int teamId, int typeId, int eventId, int times) {
        String playerId = getStringId(matchPlayer, "PlayerRef");
        String competitionId = getStringId(F9.getChild("Competition"), "uID");
        Date timestamp = GlobalDate.parseDate(F9.getChild("MatchData").getChild("MatchInfo").getAttributeValue("TimeStamp"), null);

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