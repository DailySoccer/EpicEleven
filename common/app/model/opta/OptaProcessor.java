package model.opta;

import model.GlobalDate;
import model.Model;
import model.PointsTranslation;
import org.bson.types.ObjectId;
import org.jdom2.Element;
import org.jdom2.input.JDOMParseException;
import org.jdom2.input.SAXBuilder;
import play.Logger;

import java.io.StringReader;
import java.util.*;

public class OptaProcessor {

    // Retorna los Ids de opta (gameIds, optaMachEventId) de los partidos que han cambiado
    public HashSet<String> processOptaDBInput(String feedType, String seasonCompetitionId, String name, String requestBody) {
        _dirtyMatchEventIds = new HashSet<>();

        try {
            if (isDocumentValidForProcessing(feedType, seasonCompetitionId)) {
                Element requestBodyElement = new SAXBuilder().build(new StringReader(requestBody)).getRootElement();

                if (feedType.equals("F9")) {
                    processF9(requestBodyElement);
                }
                else
                if (feedType.equals("F40")) {
                    processF40(requestBodyElement, ensureCompetition(requestBodyElement, seasonCompetitionId));
                }
                else
                if (feedType.equals("F24")) {
                    processF24(requestBodyElement);
                }
                else
                if (feedType.equals("F1")) {
                    processF1(requestBodyElement);
                }
            }
        }
        catch (JDOMParseException parseEx) {
            Logger.error("WTF 6312, {}, {}, {}", feedType, name, parseEx.getMessage());
        }
        catch (Exception e) {
            Logger.error("WTF 6313, {}, {}, {}", feedType, name, e);
        }

        return _dirtyMatchEventIds;
    }

    static private OptaCompetition ensureCompetition(Element f40, String seasonCompetitionId) {

        OptaCompetition ret = OptaCompetition.findOne(seasonCompetitionId);

        if (ret == null) {
            Element myF40 = f40.getChild("SoccerDocument");

           ret = new OptaCompetition(myF40.getAttribute("competition_id").getValue(),
                                     myF40.getAttribute("competition_code").getValue(),
                                     myF40.getAttribute("competition_name").getValue(),
                                     myF40.getAttribute("season_id").getValue());
            // Por convenio
            ret.activated = false;

            Model.optaCompetitions().insert(ret);
        }

        return ret;
    }

    static private boolean isDocumentValidForProcessing(String feedType, String seasonCompetitionId) {
        boolean valid = false;

        // Solo procesamos documentos de competiciones activas
        if (feedType.equals("F9") || feedType.equals("F24") || feedType.equals("F1")) {
            OptaCompetition optaCompetition = OptaCompetition.findOne(seasonCompetitionId);
            valid = (optaCompetition != null) && optaCompetition.activated;
        }
        else
        if (feedType.equals("F40")) {
            valid = true;   // Los F40 siempre nos resulta interesante mirar al menos si es una nueva competicion
        }

        return valid;
    }

    //
    // Crea OptaEvents
    //
    private void processF24(Element gamesObj) {

        // El cache de puntos es necesario regenarlo pq entre dos ficheros F24 puede cambiar la tabla (por ejemplo al
        // correr el simulador respetando un snapshot)
        resetPointsTranslationCache();

        Element game = gamesObj.getChild("Game");
        _dirtyMatchEventIds.add(game.getAttributeValue("id"));

        List<Element> events = game.getChildren("Event");

        for (Element event : events) {

            Date timestamp = GlobalDate.parseDate(event.getAttributeValue("last_modified"), null);

            HashMap<String, Date> eventsCache = getOptaEventsCache(game.getAttributeValue("id"));
            String eventId = event.getAttributeValue("id");

            if (!eventsCache.containsKey(eventId) || timestamp.after(eventsCache.get(eventId))) {
                OptaEvent optaEvent = new OptaEvent(event, game);
                updateEvent(optaEvent);

                eventsCache.put(eventId, timestamp);
            }
        }
    }

    private void updateEvent(OptaEvent optaEvent) {
        // Borrar evento:
        // - Si es un evento borrado por Opta
        // - Si es un evento que teníamos, pero ha cambiado y ahora es un evento que no nos interesa
        if (optaEvent.typeId == 43 || optaEvent.typeId == OptaEventType._INVALID_.code) {
            Model.optaEvents().remove("{eventId: #, teamId: #, gameId: #}", optaEvent.eventId, optaEvent.teamId, optaEvent.gameId);
        }
        else {
            optaEvent.points = getPointsTranslation(optaEvent.typeId, optaEvent.timestamp);
            optaEvent.pointsTranslationId = _pointsTranslationTableCache.get(optaEvent.typeId);

            Model.optaEvents().update("{eventId: #, teamId: #, gameId: #}", optaEvent.eventId, optaEvent.teamId, optaEvent.gameId).upsert().with(optaEvent);
        }
    }

    public void recalculateAllEvents() {
        resetPointsTranslationCache();

        for (OptaEvent optaEvent : Model.optaEvents().find().as(OptaEvent.class)) {
            updateEvent(optaEvent);
        }
    }

    //
    // Crea nuevos partidos OptaMatchEvent
    //
    private void processF1(Element f1) {

        Element myF1 = f1.getChild("SoccerDocument");
        String competitionId = myF1.getAttributeValue("competition_id");

        for (Element matchObject : myF1.getChildren("MatchData")) {

            HashMap<String, Date> optaMatchDatas = getOptaMatchDataCache(competitionId);

            String matchId = matchObject.getAttributeValue("uID");
            Date timestamp = GlobalDate.parseDate(matchObject.getAttributeValue("last_modified"), null);

            if (!optaMatchDatas.containsKey(matchId) || timestamp.after(optaMatchDatas.get(matchId))) {

                OptaMatchEvent optaMatchEvent = new OptaMatchEvent(myF1, matchObject, matchObject.getChild("MatchInfo"));
                Model.optaMatchEvents().update("{optaMatchEventId: #}", optaMatchEvent.optaMatchEventId).upsert().with(optaMatchEvent);

                optaMatchDatas.put(matchId, timestamp);
            }
        }
    }

    //
    // Crea OptaTeams y OptaPlayers
    //
    private void processF40(Element f40, OptaCompetition optaCompetition) {

        if (!optaCompetition.activated)
            return;

        if (null == _pointsTranslationCache)
            resetPointsTranslationCache();

        for (Element team : f40.getChild("SoccerDocument").getChildren("Team")) {

            List<Element> playersList = team.getChildren("Player");

            if (playersList == null) // Si es un equipo placeholder nos lo saltamos
                continue;

            OptaTeam myTeam = new OptaTeam(team);
            Model.optaTeams().update("{optaTeamId: #}", myTeam.optaTeamId).upsert()
                             .with("{$set: {optaTeamId:#, name:#, shortName:#, updatedTime:#, dirty:#}, $addToSet: {seasonCompetitionIds:#}}",
                                     myTeam.optaTeamId, myTeam.name, myTeam.shortName, myTeam.updatedTime, myTeam.dirty, optaCompetition.seasonCompetitionId);

            for (Element player : playersList) {
                OptaPlayer myPlayer = new OptaPlayer(player, team);
                Model.optaPlayers().update("{optaPlayerId: #}", myPlayer.optaPlayerId).upsert().with(myPlayer);
            }
        }
    }

    //
    // Crea exclusivamente eventos de fin de partido: CleanSheet, GoalsAgainst y OptaMatchEventStats
    //
    private void processF9(Element f9) {

        if (null == _pointsTranslationCache)
            resetPointsTranslationCache();

        // Obtener las estadísticas (minutos jugados por los futbolistas) y eventos (cleanSheet, goalsAgainst)
        Element myF9 = f9.getChild("SoccerDocument");

        if (myF9.getAttribute("Type").getValue().equals("Result")) {
            processFinishedMatch(myF9);
        }
    }

    private void processFinishedMatch(Element F9) {

        String competitionId = getStringId(F9.getChild("Competition"), "uID");
        String gameId = getStringId(F9, "uID");
        _dirtyMatchEventIds.add(gameId);

        List<Element> teamDatas = F9.getChild("MatchData").getChildren("TeamData");

        Model.optaEvents().remove("{typeId: { $in: [#, #] }, gameId: #, competitionId: #}",
                OptaEventType.CLEAN_SHEET.code, OptaEventType.GOAL_CONCEDED.code, gameId, competitionId);

        for (Element teamData : teamDatas) {
            List<Element> teamStats = teamData.getChildren("Stat");

            boolean cleanSheet = true;
            for (Element teamStat : teamStats) {
                if (teamStat.getAttribute("Type").getValue().equals("goals_conceded")) {
                    cleanSheet = false;
                    break;
                }
            }
            processGoalsConcededOrCleanSheet(F9, gameId, teamData, cleanSheet);

        }

        OptaMatchEventStats stats = new OptaMatchEventStats(gameId, teamDatas);
        Model.optaMatchEventStats().update("{optaMatchEventId: #}", gameId).upsert().with(stats);
    }


    private void processGoalsConcededOrCleanSheet(Element F9, String gameId, Element teamData, boolean cleanSheet) {
        int teamRef = Integer.parseInt(getStringId(teamData,"TeamRef"));

        for (Element matchPlayer : teamData.getChild("PlayerLineUp").getChildren("MatchPlayer")) {

            if (matchPlayer.getAttribute("Position").getValue().equals("Goalkeeper") ||
                    matchPlayer.getAttribute("Position").getValue().equals("Defender")) {

                for (Element playerStat : matchPlayer.getChildren("Stat")) {

                    //Si hay cleanSheet, una vez que encontramos la métrica de minutos jugados hacemos el break
                    if (cleanSheet) {
                        if (playerStat.getAttribute("Type").getValue().equals("mins_played")) {
                            if (Integer.parseInt(playerStat.getContent().get(0).getValue()) > 59) {
                                createEvent(F9, gameId, matchPlayer, teamRef, OptaEventType.CLEAN_SHEET.code, 20000, 1);
                            }
                            break;
                        }
                    } else {
                        //Una vez que encontramos la métrica de goles concedidos, hacemos el break si no hay cleanSheet
                        if (playerStat.getAttribute("Type").getValue().equals("goals_conceded")) {
                            //Si al jugador le han metido más de un gol
                            int playersGoalsConceded = Integer.parseInt(playerStat.getContent().get(0).getValue());
                            if (playersGoalsConceded > 0) {
                                createEvent(F9, gameId, matchPlayer, teamRef, OptaEventType.GOAL_CONCEDED.code, 20001,
                                        playersGoalsConceded);
                            }
                            break;
                        }
                    }
                }
            }
        }
    }


    private void createEvent(Element F9, String gameId, Element matchPlayer, int teamId, int typeId, int eventId, int times) {
        String playerId = getStringId(matchPlayer, "PlayerRef");
        String competitionId = getStringId(F9.getChild("Competition"), "uID");
        String seasonId = getF9SeasonId(F9);

        Date timestamp = GlobalDate.parseDate(F9.getChild("MatchData").getChild("MatchInfo").getAttributeValue("TimeStamp"), null);

        int points = getPointsTranslation(typeId, timestamp) * times;
        ObjectId pointsTranslationId = _pointsTranslationTableCache.get(typeId);

        OptaEvent myEvent = new OptaEvent(typeId, eventId, playerId, teamId, gameId, competitionId,
                                          seasonId, timestamp, points, pointsTranslationId);

        Model.optaEvents().insert(myEvent);
    }


    private String getF9SeasonId(Element F9) {
        String seasonId = null;

        List<Element> competitionStats = F9.getChild("Competition").getChildren("Stat");
        for (Element competitionStat : competitionStats) {
            if (competitionStat.getAttribute("Type").getValue().equals("season_id")) {
                seasonId = String.valueOf(competitionStat.getContent().get(0).getValue());
            }
        }
        return seasonId;
    }


    static public String getStringId(Element document, String key) {

        if (document.getAttribute(key) == null) {
            throw new RuntimeException("WTF 2906: " + key);
        }

        String value = document.getAttributeValue(key);
        return Character.isDigit(value.charAt(0))? value : value.substring(1);
    }

    private void resetPointsTranslationCache() {
        _pointsTranslationCache = new HashMap<Integer, Integer>();
        _pointsTranslationTableCache = new HashMap<Integer, ObjectId>();
    }

    private int getPointsTranslation(int typeId, Date timestamp){
        if (_pointsTranslationCache.containsKey(typeId)) {
            return _pointsTranslationCache.get(typeId);
        }
        Iterable<PointsTranslation> pointsTranslations = Model.pointsTranslation()
                                                              .find("{eventTypeId: #, timestamp: {$lte: #}}", typeId, timestamp)
                                                              .sort("{timestamp: -1}").as(PointsTranslation.class);

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

    private HashMap<String, Date> getOptaEventsCache(String gameId) {
        if (_optaEventsCache == null) {
            _optaEventsCache = new HashMap<String, HashMap<String, Date>>();
        }
        if (!_optaEventsCache.containsKey(gameId)) {
            _optaEventsCache.put(gameId, new HashMap<String, Date>());
        }
        return _optaEventsCache.get(gameId);
    }

    private HashMap<String, Date> getOptaMatchDataCache(String competitionId) {
        if (_optaMatchDataCache == null) {
            _optaMatchDataCache = new HashMap<String, HashMap<String, Date>>();
        }
        if (!_optaMatchDataCache.containsKey(competitionId)) {
            _optaMatchDataCache.put(competitionId, new HashMap<String, Date>());
        }
        return _optaMatchDataCache.get(competitionId);
    }

    private HashSet<String> _dirtyMatchEventIds;

    private HashMap<Integer, Integer> _pointsTranslationCache;
    private HashMap<Integer, ObjectId> _pointsTranslationTableCache;

    private HashMap<String, HashMap<String, Date>> _optaEventsCache;
    private HashMap<String, HashMap<String, Date>> _optaMatchDataCache;
}