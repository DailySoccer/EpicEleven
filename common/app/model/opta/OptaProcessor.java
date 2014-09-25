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
    public HashSet<String> processOptaDBInput(String feedType, String seasonCompetitionId, String name,
                                              String requestBody, String competitionId, String seasonId, String gameId) {
        _dirtyMatchEventIds = new HashSet<>();
        _competitionId = competitionId;
        _seasonId = seasonId;
        _gameId = gameId;

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
                updateEvent(new OptaEvent(event, game));
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
            PointsTranslation pointsTranslation = getPointsTranslation(optaEvent.typeId, optaEvent.timestamp);
            optaEvent.points = pointsTranslation.points;
            optaEvent.pointsTranslationId = pointsTranslation.pointsTranslationId;

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

            // Obtenemos la lista de players que están actualmente en el equipo
            // Recorremos la lista de players que Opta nos proporciona en el F40
            // Diferenciamos entre los players que
            // - sean nuevos (no estaban aún en nuestra equipo)
            //      serán añadidos a nuestra base de datos (en el caso de no existir), o los actualizaremos para que registren el nuevo equipo
            // - hayan modificado sus datos
            //      actualizamos su información
            // - no permanecen en el equipo (no aparecen en la lista de Opta)
            //      los marcamos con un flag (INVALID_TEAM)
            HashMap<String, OptaPlayer> optaPlayers = OptaPlayer.asMap(OptaPlayer.findAllFromTeam(myTeam.optaTeamId));
            List<OptaPlayer> playersToInsert = new ArrayList<>();
            for (Element player : playersList) {
                OptaPlayer myPlayer = new OptaPlayer(player, team);

                // Está en el equipo?
                if (optaPlayers.containsKey(myPlayer.optaPlayerId)) {
                    // Datos nuevos?
                    if (myPlayer.hasChanged(optaPlayers.get(myPlayer.optaPlayerId))) {
                        // Actualizar
                        Model.optaPlayers().update("{optaPlayerId: #}", myPlayer.optaPlayerId).upsert().with(myPlayer);
                    }
                    optaPlayers.remove(myPlayer.optaPlayerId);
                }
                else {
                    // Lo marcamos para añadir
                    playersToInsert.add(myPlayer);
                }
            }
            // Insertamos la lista de players "nuevos"
            if (!playersToInsert.isEmpty()) {
                for (OptaPlayer playerToInsert: playersToInsert) {
                    Model.optaPlayers().update("{optaPlayerId: #}", playerToInsert.optaPlayerId).upsert().with(playerToInsert);
                }
            }
            // Marcamos como "sin equipo" a los players que no han sido enviados con el equipo (verificamos que no hayan cambiado de equipo)
            if (!optaPlayers.isEmpty()) {
                Model.optaPlayers().update("{optaPlayerId:{$in: #}, teamId:#}", optaPlayers.keySet(), myTeam.optaTeamId).with("{$set: {teamId: #, dirty: true}}", OptaTeam.INVALID_TEAM);
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

        _dirtyMatchEventIds.add(_gameId);

        List<Element> teamDatas = F9.getChild("MatchData").getChildren("TeamData");

        Model.optaEvents().remove("{typeId: { $in: [#, #] }, gameId: #, competitionId: #, seasonId: #}",
                OptaEventType.CLEAN_SHEET.code, OptaEventType.GOAL_CONCEDED.code, _gameId, _competitionId, _seasonId);


        for (Element teamData : teamDatas) {
            boolean cleanSheet = true;
            for (Element teamStat : teamData.getChildren("Stat")) {
                if (teamStat.getAttribute("Type").getValue().equals("goals_conceded")) {
                    cleanSheet = false;
                    break;
                }
            }

            processGoalsConcededOrCleanSheet(F9, teamData, cleanSheet);
        }

        OptaMatchEventStats stats = new OptaMatchEventStats(_gameId, teamDatas);
        Model.optaMatchEventStats().update("{optaMatchEventId: #}", _gameId).upsert().with(stats);
    }



    private void processGoalsConcededOrCleanSheet(Element F9, Element teamData, boolean cleanSheet) {

        int teamRef = Integer.parseInt(getStringId(teamData,"TeamRef"));

        for (Element matchPlayer : teamData.getChild("PlayerLineUp").getChildren("MatchPlayer")) {

            if (matchPlayer.getAttribute("Position").getValue().equals("Goalkeeper") ||
                    matchPlayer.getAttribute("Position").getValue().equals("Defender")) {

                for (Element playerStat : matchPlayer.getChildren("Stat")) {

                    //Si hay cleanSheet, una vez que encontramos la métrica de minutos jugados hacemos el break
                    if (cleanSheet) {
                        if (playerStat.getAttribute("Type").getValue().equals("mins_played")) {
                            if (Integer.parseInt(playerStat.getContent().get(0).getValue()) > 59) {

                                createEvent(F9, matchPlayer, teamRef,
                                        OptaEventType.CLEAN_SHEET.code, 20000, 1);
                            }
                            break;
                        }
                    } else {
                        //Una vez que encontramos la métrica de goles concedidos, hacemos el break si no hay cleanSheet
                        if (playerStat.getAttribute("Type").getValue().equals("goals_conceded")) {
                            //Si al jugador le han metido más de un gol
                            int playersGoalsConceded = Integer.parseInt(playerStat.getContent().get(0).getValue());
                            if (playersGoalsConceded > 0) {

                                createEvent(F9, matchPlayer, teamRef,
                                        OptaEventType.GOAL_CONCEDED.code, 20001, playersGoalsConceded);
                            }
                            break;
                        }
                    }
                }
            }
        }
    }

    private void createEvent(Element F9, Element matchPlayer, int teamId, int typeId, int eventId, int times) {

        String playerId = getStringId(matchPlayer, "PlayerRef");
        Date timestamp = GlobalDate.parseDate(F9.getChild("MatchData").getChild("MatchInfo").getAttributeValue("TimeStamp"), null);

        PointsTranslation pointsTranslation = getPointsTranslation(typeId, timestamp);
        int points =  pointsTranslation.points * times;
        ObjectId pointsTranslationId = pointsTranslation.pointsTranslationId;

        OptaEvent myEvent = new OptaEvent(typeId, eventId, playerId, teamId, _gameId, _competitionId,
                                          _seasonId, timestamp, points, pointsTranslationId);

        Model.optaEvents().insert(myEvent);
    }


    static public String getStringId(Element document, String key) {

        if (document.getAttribute(key) == null) {
            throw new RuntimeException("WTF 2906: " + key);
        }

        String value = document.getAttributeValue(key);
        return Character.isDigit(value.charAt(0))? value : value.substring(1);
    }

    private void resetPointsTranslationCache() {
        _pointsTranslationCache = new HashMap<>();
    }

    private PointsTranslation getPointsTranslation(int typeId, Date timestamp) {

        PointsTranslation ret = _pointsTranslationCache.get(typeId);

        if (ret == null) {
            Iterable<PointsTranslation> pointsTranslations = Model.pointsTranslation()
                                                                  .find("{eventTypeId: #, timestamp: {$lte: #}}", typeId, timestamp)
                                                                  .sort("{timestamp: -1}").as(PointsTranslation.class);

            if (pointsTranslations.iterator().hasNext()) {
                ret = pointsTranslations.iterator().next();
            }
            else {
                // No tenemos traduccion de puntos para este evento. Devolvemos puntos=0, pointsTranslationId = null
                ret = new PointsTranslation();
            }
            _pointsTranslationCache.put(typeId, ret);
        }

        return ret;
    }

    private HashMap<String, Date> getOptaEventsCache(String gameId) {
        if (_optaEventsCache == null) {
            _optaEventsCache = new HashMap<>();
        }
        if (!_optaEventsCache.containsKey(gameId)) {
            _optaEventsCache.put(gameId, new HashMap<String, Date>());
        }
        return _optaEventsCache.get(gameId);
    }

    private HashMap<String, Date> getOptaMatchDataCache(String competitionId) {
        if (_optaMatchDataCache == null) {
            _optaMatchDataCache = new HashMap<>();
        }
        if (!_optaMatchDataCache.containsKey(competitionId)) {
            _optaMatchDataCache.put(competitionId, new HashMap<String, Date>());
        }
        return _optaMatchDataCache.get(competitionId);
    }

    private HashSet<String> _dirtyMatchEventIds;

    private HashMap<Integer, PointsTranslation> _pointsTranslationCache;

    private HashMap<String, HashMap<String, Date>> _optaEventsCache;
    private HashMap<String, HashMap<String, Date>> _optaMatchDataCache;

    private String _gameId;
    private String _seasonId;
    private String _competitionId;
}