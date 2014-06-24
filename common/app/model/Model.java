package model;

import com.mongodb.*;
import org.jongo.Jongo;
import org.jongo.Find;
import org.jongo.MongoCollection;
import play.Logger;
import play.Play;
import org.bson.types.ObjectId;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;

import model.opta.*;
import utils.ListUtils;
import java.util.Date;


public class Model {
    static public DB mongoDB() { return _mongoDB; }
    static public Jongo jongo() { return _jongo; }
    static public MongoCollection sessions() { return _jongo.getCollection("sessions"); }
    static public MongoCollection users() { return _jongo.getCollection("users"); }

    static public MongoCollection templateContests() { return _jongo.getCollection("templateContests"); }
    static public MongoCollection templateMatchEvents() { return _jongo.getCollection("templateMatchEvents"); }
    static public MongoCollection templateSoccerTeams() { return _jongo.getCollection("templateSoccerTeams"); }
    static public MongoCollection templateSoccerPlayers() { return _jongo.getCollection("templateSoccerPlayers"); }
    static public MongoCollection liveMatchEvents() { return _jongo.getCollection("liveMatchEvents"); }
    static public MongoCollection contestEntries() { return _jongo.getCollection("contestEntries"); }

    static public MongoCollection contests() { return _jongo.getCollection("contests"); }
    static public MongoCollection matchEvents() { return _jongo.getCollection("matchEvents"); }

    static public MongoCollection optaDB() { return _jongo.getCollection("optaDB"); }
    static public MongoCollection optaEvents() { return _jongo.getCollection("optaEvents"); }
    static public MongoCollection optaPlayers() { return _jongo.getCollection("optaPlayers"); }
    static public MongoCollection optaTeams() { return _jongo.getCollection("optaTeams"); }
    static public MongoCollection optaMatchEvents() { return _jongo.getCollection("optaMatchEvents"); }
    static public MongoCollection pointsTranslation() { return _jongo.getCollection("pointsTranslation"); }

    static public void init() {
        String mongodbUri = Play.application().configuration().getString("mongodb.uri");
        MongoClientURI mongoClientURI = new MongoClientURI(mongodbUri);

        Logger.info("The MongoDB is {}/{}", mongoClientURI.getHosts(), mongoClientURI.getDatabase());

        boolean bIsInitialized = false;
        while (!bIsInitialized) {
            try {
                _mongoClient = new MongoClient(mongoClientURI);
                _mongoDB = _mongoClient.getDB(mongoClientURI.getDatabase());
                _jongo = new Jongo(_mongoDB);

                // Let's make sure our DB has the neccesary collections and indexes
                ensureDB(_mongoDB);

                bIsInitialized = true;
            }
            catch (Exception exc) {
                Logger.error("Error initializating MongoDB {}/{}: {}", mongoClientURI.getHosts(),
                                                                       mongoClientURI.getDatabase(), exc.toString());

                if (exc instanceof MongoServerSelectionException && !Play.isProd()) {
                    try {
                        Logger.info("Mongodb seems to be off. Attempting to start it up.");
                        LogInputStream(Runtime.getRuntime().exec("mongod run --config /usr/local/etc/mongod.conf"));
                        WaitSeconds(2, "Waiting for mongod to start");
                    }
                    catch (Exception e) {
                        WaitSeconds(10, "Trying to initialize MongoDB again");
                    }
                }
                else {
                    WaitSeconds(10, "Trying to recover from an unknown exception");
                }
            }
        }
    }

    static void LogInputStream(Process p) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
        String line = "";
        while ((line = reader.readLine())!= null)
            Logger.info(line);
    }

    static void WaitSeconds(int seconds, String message) {
        try {
            Logger.info("{} in {} seconds...", message, seconds);
            Thread.sleep(seconds * 1000);
        } catch (InterruptedException intExc) {
            Logger.error("Interrupted");
        }
    }

    static public void shutdown() {
        if (_mongoClient != null)
            _mongoClient.close();
    }

    static private String[] contestCollectionNames = {
            "templateContests",
            "templateMatchEvents",
            "templateSoccerTeams",
            "templateSoccerPlayers",
            "contests",
            "matchEvents",
            "liveMatchEvents",
            "contestEntries"
    };

    static private void clearContestsDB(DB theMongoDB) {
        for (String name : contestCollectionNames) {
            theMongoDB.getCollection(name).drop();
        }
    }

    static private void ensureContestsDB(DB theMongoDB) {
        for (String name : contestCollectionNames) {
            if (!theMongoDB.collectionExists(name))
                theMongoDB.createCollection(name, new BasicDBObject());
        }
    }

    static private void clearDB(DB theMongoDB) {
        // Indicamos las collections a borrar (no incluidas como "contestCollection"
        final String[] collectionNames = {
                "users",
                "sessions"
        };

        // Eliminar las collections genericas
        for (String name : collectionNames) {
            theMongoDB.getCollection(name).drop();
        }

        clearContestsDB(theMongoDB);
    }

    static private void ensureDB(DB theMongoDB) {
        DBCollection users = theMongoDB.getCollection("users");

        // Si creando nuevo indice sobre datos que ya existan => .append("dropDups", true)
        users.createIndex(new BasicDBObject("email", 1), new BasicDBObject("unique", true));
        users.createIndex(new BasicDBObject("nickName", 1), new BasicDBObject("unique", true));

        // Do we need the sessionToken to be unique? SecureRandom guarantees it to be unique, doesn't it?
        // http://www.kodyaz.com/images/pics/random-number-generator-dilbert-comic.jpg
        DBCollection sessions = theMongoDB.getCollection("sessions");
        sessions.createIndex(new BasicDBObject("sessionToken", 1), new BasicDBObject("unique", true));

        DBCollection optaDB = theMongoDB.getCollection("optaDB");
        optaDB.createIndex(new BasicDBObject("startDate", 1));
        optaDB.createIndex(new BasicDBObject("startDate", -1));
        DBCollection optaEvents = theMongoDB.getCollection("optaEvents");
        optaEvents.createIndex(new BasicDBObject("parentId", 1));
        optaEvents.createIndex(new BasicDBObject("eventId", 1));
        optaEvents.createIndex(new BasicDBObject("gameId", 1));
        optaEvents.createIndex(new BasicDBObject("optaPlayerId", 1));
        DBCollection optaPlayers = theMongoDB.getCollection("optaPlayers");
        optaEvents.createIndex(new BasicDBObject("optaPlayerId", 1));
        DBCollection optaTeams = theMongoDB.getCollection("optaTeams");
        DBCollection optaMatchEvents = theMongoDB.getCollection("optaMatchEvents");
        DBCollection pointsTranslation = theMongoDB.getCollection("pointsTranslation");
        pointsTranslation.createIndex(new BasicDBObject("eventTypeId", 1));

        ensureContestsDB(theMongoDB);
    }

    static public void resetDB() {
        clearDB(_mongoDB);
        ensureDB(_mongoDB);
    }

    static public void resetContests() {
        clearContestsDB(_mongoDB);
        ensureContestsDB(_mongoDB);
    }

    static public void resetOpta() {
        optaEvents().remove();
        optaPlayers().remove();
        optaTeams().remove();
        optaMatchEvents().remove();
    }

    /**
     *  Utilidades para acceder a los datos
     *
     */

    static public LiveMatchEvent liveMatchEvent(ObjectId liveMatchEventId) {
        return liveMatchEvents().findOne("{_id : #}", liveMatchEventId).as(LiveMatchEvent.class);
    }

    static public ContestEntry contestEntry(ObjectId contestEntryId) {
        return contestEntries().findOne("{_id : #}", contestEntryId).as(ContestEntry.class);
    }

    static public Contest contest(ObjectId contestId) {
        return contests().findOne("{_id : #}", contestId).as(Contest.class);
    }

    static public TemplateContest templateContest(ObjectId templateContestId) {
        return templateContests().findOne("{_id : #}", templateContestId).as(TemplateContest.class);
    }

    static public TemplateMatchEvent templateMatchEvent(ObjectId templateMatchEventId) {
        return templateMatchEvents().findOne("{_id : #}", templateMatchEventId).as(TemplateMatchEvent.class);
    }

    static public TemplateSoccerPlayer templateSoccerPlayer(ObjectId templateSoccerPlayerId) {
        return templateSoccerPlayers().findOne("{_id : #}", templateSoccerPlayerId).as(TemplateSoccerPlayer.class);
    }

    static public LiveMatchEvent liveMatchEvent(TemplateMatchEvent templateMatchEvent) {
        // Buscamos el "live" a partir de su "template"
        LiveMatchEvent liveMatchEvent = liveMatchEvents().findOne("{templateMatchEventId: #}", templateMatchEvent.templateMatchEventId).as(LiveMatchEvent.class);
        if (liveMatchEvent == null) {
            // Si no existe y el partido "ha comenzado"...
            if (Model.isMatchEventStarted(templateMatchEvent)) {
                // ... creamos su version "live"
                liveMatchEvent = new LiveMatchEvent(templateMatchEvent);
                // Generamos el objectId para poder devolverlo correctamente
                liveMatchEvent.liveMatchEventId = new ObjectId();
                liveMatchEvents().insert(liveMatchEvent);
            }
        }
        return liveMatchEvent;
    }

    static public List<TemplateMatchEvent> templateMatchEvents(TemplateContest templateContest) {
        Iterable<TemplateMatchEvent> templateMatchEventResults = findTemplateMatchEventFromIds("_id", templateContest.templateMatchEventIds);
        return ListUtils.asList(templateMatchEventResults);
    }

    static public List<OptaEvent> optaEvents(String optaMatchId, String optaPlayerId) {
        String playerId = getPlayerIdFromOpta(optaPlayerId);
        String matchId = getMatchEventIdFromOpta(optaMatchId);

        Iterable<OptaEvent> optaEventResults = optaEvents().find("{optaPlayerId: #, gameId: #}",
                playerId, matchId).as(OptaEvent.class);
        return ListUtils.asList(optaEventResults);
    }

    /**
     * Inicializar la coleccion de equipos y futbolistas (que usaremos para crear los match events)
     * a partir de la coleccion de datos actualizada desde optaDB
     */
    static public void importTeamsAndSoccersFromOptaDB() {
        long startTime = System.currentTimeMillis();

        try {

            HashMap<String, TemplateSoccerTeam> teamsMap = new HashMap<>();
            Iterable<OptaTeam> optaTeams = Model.optaTeams().find().as(OptaTeam.class);
            for (OptaTeam optaTeam : optaTeams) {
                TemplateSoccerTeam templateTeam = new TemplateSoccerTeam(optaTeam);
                templateTeam.templateSoccerTeamId = new ObjectId();
                Model.templateSoccerTeams().withWriteConcern(WriteConcern.SAFE).insert(templateTeam);

                teamsMap.put(templateTeam.optaTeamId, templateTeam);
            }

            Iterable<OptaPlayer> optaPlayers = Model.optaPlayers().find().as(OptaPlayer.class);
            for (OptaPlayer optaPlayer : optaPlayers) {
                ObjectId teamId = null;
                if (teamsMap.containsKey(optaPlayer.teamId)) {
                    teamId = teamsMap.get(optaPlayer.teamId).templateSoccerTeamId;

                    TemplateSoccerPlayer templatePlayer = new TemplateSoccerPlayer(optaPlayer, teamId);
                    Model.templateSoccerPlayers().withWriteConcern(WriteConcern.SAFE).insert(templatePlayer);
                }
            }

        } catch (MongoException exc) {
            Logger.error("importTeamsAndSoccersFromOptaDB: ", exc);
        }

        Logger.info("import Teams&Soccers: {}", System.currentTimeMillis() - startTime);
    }

    /**
     * Inicializar la coleccion de partidos
     *  a partir de la coleccion de datos actualizada desde optaDB
     */
    static public void importMatchEventsFromOptaDB() {
        long startTime = System.currentTimeMillis();

        try {
            Iterable<OptaMatchEvent> optaMatchEvents = Model.optaMatchEvents().find().as(OptaMatchEvent.class);
            for (OptaMatchEvent optaMatch: optaMatchEvents) {
                TemplateSoccerTeam teamA = templateSoccerTeams().findOne("{optaTeamId: #}", optaMatch.homeTeamId).as(TemplateSoccerTeam.class);
                TemplateSoccerTeam teamB = templateSoccerTeams().findOne("{optaTeamId: #}", optaMatch.awayTeamId).as(TemplateSoccerTeam.class);
                if (teamA != null && teamB != null) {
                    createTemplateMatchEvent(optaMatch.id, teamA, teamB, optaMatch.matchDate);
                }
                else {
                    Logger.info("Ignorando OptaMatchEvent: {} ({})", optaMatch.id, optaMatch.matchDate);
                }
            }
        } catch (MongoException exc) {
            Logger.error("importTeamsAndSoccersFromOptaDB: ", exc);
        }

        Logger.info("import Teams&Soccers: {}", System.currentTimeMillis() - startTime);
    }

    /**
     * Creacion de una entrada de Points Translation
     */
    public static boolean createPointsTranslation(int eventType, int points) {
        PointsTranslation pointsTranslation = new PointsTranslation();
        pointsTranslation.eventTypeId = eventType;
        pointsTranslation.points = points;
        pointsTranslation.timestamp = new Date();
        pointsTranslation.unixtimestamp = pointsTranslation.timestamp.getTime();
        pointsTranslation().insert(pointsTranslation);
        return true;
    }

    /**
     *  Eliminar un contest entry y sus dependencias
     */
    public static boolean deleteContestEntry(ContestEntry contestEntry) {
        Logger.info("delete ContestEntry ({})", contestEntry.contestEntryId);
        contestEntries().remove(contestEntry.contestEntryId);

        return true;
    }

    /**
     * Eliminar un contest y sus dependencias
     */
    public static boolean deleteContest(Contest contest) {
        Logger.info("delete Contest ({}): {}", contest.contestId, contest.name);

        // Eliminar los contest entries de ese contest
        contestEntries().remove("{contestId: #}", contest.contestId);

        // Eliminar el contest
        contests().remove(contest.contestId);

        return true;
    }

    /**
     *  Eliminar un template contest y sus dependencias
     */
    public static boolean deleteTemplateContest(TemplateContest templateContest) {
        Logger.info("delete TemplateContest({}): {}", templateContest.templateContestId, templateContest.name);

        // Buscar los Contests que instancian el template contest
        Iterable<Contest> contestResults = Model.contests().find("{templateContestId : #}", templateContest.templateContestId).as(Contest.class);
        List<Contest> contestList = ListUtils.asList(contestResults);

        List<ObjectId> contestIds = new ArrayList<>();
        for (Contest contest : contestList) {
            deleteContest(contest);
        }

        // Eliminar el template contest
        templateContests().remove(templateContest.templateContestId);

        return true;
    }

    /**
     * Creacion de un contest entry (se añade a la base de datos)
     * @param userId        Usuario al que pertenece el equipo
     * @param contestId     Contest al que se apunta
     * @param optaIdsList   Lista de identificadores de los futbolistas de Opta
     * @return Si se ha realizado correctamente su creacion
     */
    public static boolean createContestEntryFromOptaIds(String userId, String contestId, List<String> optaIdsList) {
        Logger.info("createContestEntry: userId({}) contestId({}) soccerTeam({})", userId, contestId, optaIdsList);

        // Obtener el userId : ObjectId
        User aUser = Model.findUserId(userId);
        if (aUser == null) {
            return false;
        }

        // Obtener el contestId : ObjectId
        Contest aContest = Model.findContestId(contestId);
        if (aContest == null) {
            return false;
        }

        // Obtener los soccerIds de los futbolistas : List<ObjectId>
        List<ObjectId> soccerIds = new ArrayList<>();

        Iterable<TemplateSoccerPlayer> soccers = Model.findFields(Model.templateSoccerPlayers(), "optaPlayerId", optaIdsList).as(TemplateSoccerPlayer.class);

        String soccerNames = "";    // Requerido para Logger.info
        for (TemplateSoccerPlayer soccer : soccers) {
            soccerNames += soccer.name + " / ";
            soccerIds.add(soccer.templateSoccerPlayerId);
        }

        Logger.info("contestEntry: Contest[{}] / User[{}] = ({}) => {}", aContest.name, aUser.nickName, soccerIds.size(), soccerNames);

        // Crear el equipo en mongoDb.contestEntryCollection
        return createContestEntry(new ObjectId(userId), new ObjectId(contestId), soccerIds);
    }

    /**
     * Creacion de un contest entry (se añade a la base de datos)
     * @param user      Usuario al que pertenece el equipo
     * @param contestId   Contest al que se apunta
     * @param soccers   Lista de futbolistas con la que se apunta
     * @return Si se ha realizado correctamente su creacion
     */
    public static boolean createContestEntry(ObjectId user, ObjectId contestId, List<ObjectId> soccers) {
        boolean bRet = true;

        try {
            Contest contest = contests().findOne("{ _id: # }", contestId).as(Contest.class);
            if (contest != null) {
                ContestEntry aContestEntry = new ContestEntry(user, contestId, soccers);
                Model.contestEntries().withWriteConcern(WriteConcern.SAFE).insert(aContestEntry);

                if (!contest.currentUserIds.contains(contest.contestId)) {
                    contest.currentUserIds.add(contest.contestId);
                    Model.contests().update(contest.contestId).with(contest);
                }
            }
            else {
                bRet = false;
            }



        } catch (MongoException exc) {
            Logger.error("createContestEntry: ", exc);
            bRet = false;
        }

        return bRet;
    }

    /**
     * Crea un template match event
     * @param teamA     TeamA
     * @param teamB     TeamB
     * @param startDate Cuando se jugara el partido
     * @return El template match event creado
     */
    static public TemplateMatchEvent createTemplateMatchEvent(TemplateSoccerTeam teamA, TemplateSoccerTeam teamB, Date startDate) {
        return createTemplateMatchEvent(null, teamA, teamB, startDate);
    }

    static public TemplateMatchEvent createTemplateMatchEvent(String optaMatchEventId, TemplateSoccerTeam teamA, TemplateSoccerTeam teamB, Date startDate) {
        Logger.info("Template MatchEvent: {} vs {} ({})", teamA.name, teamB.name, startDate);

        TemplateMatchEvent templateMatchEvent = new TemplateMatchEvent();
        templateMatchEvent.startDate = startDate;
        templateMatchEvent.optaMatchEventId = optaMatchEventId;

        // setup Team A (incrustando a los futbolistas en el equipo)
        SoccerTeam newTeamA = new SoccerTeam();
        newTeamA.templateSoccerTeamId = teamA.templateSoccerTeamId;
        newTeamA.optaTeamId = teamA.optaTeamId;
        newTeamA.name = teamA.name;
        Iterable<TemplateSoccerPlayer> playersTeamA = Model.templateSoccerPlayers().find("{ templateTeamId: # }", teamA.templateSoccerTeamId).as(TemplateSoccerPlayer.class);
        for(TemplateSoccerPlayer templateSoccer : playersTeamA) {
            newTeamA.soccerPlayers.add(new SoccerPlayer(templateSoccer));
        }
        templateMatchEvent.soccerTeamA = newTeamA;

        // setup Team B (incrustando a los futbolistas en el equipo)
        SoccerTeam newTeamB = new SoccerTeam();
        newTeamB.templateSoccerTeamId = teamB.templateSoccerTeamId;
        newTeamB.optaTeamId = teamB.optaTeamId;
        newTeamB.name = teamB.name;
        Iterable<TemplateSoccerPlayer> playersTeamB = Model.templateSoccerPlayers().find("{ templateTeamId: # }", teamB.templateSoccerTeamId).as(TemplateSoccerPlayer.class);
        for(TemplateSoccerPlayer templateSoccer : playersTeamB) {
            newTeamB.soccerPlayers.add(new SoccerPlayer(templateSoccer));
        }
        templateMatchEvent.soccerTeamB = newTeamB;

        // TODO: Eliminar condicion (optaMatchEventId == null)
        if (optaMatchEventId != null) {
            // Insertar o actualizar
            Model.templateMatchEvents().update("{optaMatchEventId: #}", optaMatchEventId).upsert().with(templateMatchEvent);
        }
        else {
            Model.templateMatchEvents().insert(templateMatchEvent);
        }

        return templateMatchEvent;
    }

    /**
     * Actualizar los puntos fantasy de un determinado futbolista en los partidos "live"
     *
     * @param optaMatchId Identificador del partido
     * @param soccerPlayerId Identificador del futbolista
     * @param points      Puntos fantasy
     */
    static public void setLiveFantasyPointsOfSoccerPlayer(String optaMatchId, ObjectId soccerPlayerId, int points) {
        //Logger.info("setLiveFantasyPoints: {} = {} fantasy points", soccerPlayerId, strPoints);

        long startTime = System.currentTimeMillis();

        // Actualizar jugador si aparece en TeamA
        Model.liveMatchEvents()
                .update("{optaMatchEventId: #, soccerTeamA.soccerPlayers.templateSoccerPlayerId: #}", optaMatchId, soccerPlayerId)
                .multi()
                .with("{$set: {soccerTeamA.soccerPlayers.$.fantasyPoints: #}}", points);

        // Actualizar jugador si aparece en TeamB
        Model.liveMatchEvents()
                .update("{optaMatchEventId: #, soccerTeamB.soccerPlayers.templateSoccerPlayerId: #}", optaMatchId, soccerPlayerId)
                .multi()
                .with("{$set: {soccerTeamB.soccerPlayers.$.fantasyPoints: #}}", points);

        //Logger.info("END: setLiveFantasyPoints: {}", System.currentTimeMillis() - startTime);
    }

    /**
     * Calcular y actualizar los puntos fantasy de un determinado futbolista en los partidos "live"
     *
     * @param optaMatchId Partido que se ha jugado
     * @param soccerPlayer Futbolista
     */
    static public void updateLiveFantasyPoints(String optaMatchId, SoccerPlayer soccerPlayer) {
        // Logger.info("search points: {}: optaId({})", soccerPlayer.name, soccerPlayer.optaPlayerId);

        // TODO: Quitamos el primer caracter ("p": player / "g": "match")
        String playerId = getPlayerIdFromOpta(soccerPlayer.optaPlayerId);
        String matchId = getMatchEventIdFromOpta(optaMatchId);

        //TODO: ¿ $sum (aggregation) ?
        // Obtener los puntos fantasy obtenidos por el futbolista en un partido
        Iterable<OptaEvent> optaEventResults = optaEvents().find("{optaPlayerId: #, gameId: #}",
                playerId, matchId).as(OptaEvent.class);

        // Sumarlos
        int points = 0;
        for (OptaEvent point: optaEventResults) {
            points += point.points;
        }
        if (points > 0) {
            Logger.info("--> {}: {} = {}", soccerPlayer.optaPlayerId, soccerPlayer.name, points);
        }

        // optaEvents().aggregate("{$match: {optaPlayerId: #}}", soccerPlayer.optaPlayerId);

        // Actualizar sus puntos en cada LiverMatchEvent en el que participe
        setLiveFantasyPointsOfSoccerPlayer(optaMatchId, soccerPlayer.templateSoccerPlayerId, points);
    }

    /**
     * Calcular y actualizar los puntos fantasy de un determinado partido "live"
     * Opera sobre cada uno de los futbolistas del partido (teamA y teamB)
     *
     * @param liveMatchEvent Partido "live"
     */
    static public void updateLiveFantasyPoints(LiveMatchEvent liveMatchEvent) {
        Logger.info("update Live: {} vs {} ({})",
            liveMatchEvent.soccerTeamA.name, liveMatchEvent.soccerTeamB.name, liveMatchEvent.startDate);

        // Actualizamos los jugadores del TeamA
        for (SoccerPlayer soccer : liveMatchEvent.soccerTeamA.soccerPlayers) {
            updateLiveFantasyPoints(liveMatchEvent.optaMatchEventId, soccer);
        }

        // Actualizamos los jugadores del TeamB
        for (SoccerPlayer soccer : liveMatchEvent.soccerTeamB.soccerPlayers) {
            updateLiveFantasyPoints(liveMatchEvent.optaMatchEventId, soccer);
        }
    }

    /**
     * Calcular y actualizar los puntos fantasy de una lista de partidos "live"
     *
     * @param matchEventIdsList Lista de partidos "live"
     */
    static public void updateLiveFantasyPoints(List<ObjectId> matchEventIdsList) {
        Logger.info("updateLiveFantasyPoints: {}", matchEventIdsList);

        long startTime = System.currentTimeMillis();

        Iterable<LiveMatchEvent> liveMatchEventResults = Model.findLiveMatchEventsFromIds("templateMatchEventId", matchEventIdsList);
        List<LiveMatchEvent> liveMatchEventList = ListUtils.asList(liveMatchEventResults);

        for (LiveMatchEvent liveMatchEvent : liveMatchEventList) {
            updateLiveFantasyPoints(liveMatchEvent);
        }

        Logger.info("END: updateLiveFantasyPoints: {}", System.currentTimeMillis() - startTime);
    }

    /**
     *  Calcular y actualizar los puntos fantasy de un contest
     */
    static public void updateLiveFantasyPoints(Contest contest) {
        TemplateContest templateContest = templateContest(contest.templateContestId);
        updateLiveFantasyPoints(templateContest.templateMatchEventIds);
    }

    /**
     *  Calcular y actualizar los puntos fantasy de un contest entry
     */
    static public void updateLiveFantasyPoints(ContestEntry contestEntry) {
        Contest contest = Model.contest(contestEntry.contestId);
        updateLiveFantasyPoints(contest);
    }

    /**
     * Query de un usuario por su identificador en mongoDB (verifica la validez del mismo)
     *
     * @param userId Identificador del usuario
     * @return User
     */
    static public User findUserId(String userId) {
        User aUser = null;
        Boolean userValid = ObjectId.isValid(userId);
        if (userValid) {
            aUser = Model.users().findOne(new ObjectId(userId)).as(User.class);
        }
        return aUser;
    }

    /**
     * Query de un contest por su identificador en mongoDB (verifica la validez del mismo)
     *
     * @param contestId Identficador del contest
     * @return Contest
     */
    static public Contest findContestId(String contestId) {
        Contest aContest = null;
        Boolean userValid = ObjectId.isValid(contestId);
        if (userValid) {
            aContest = Model.contests().findOne(new ObjectId(contestId)).as(Contest.class);
        }
        return aContest;
    }

    /**
     *  Query de la lista de Template Contests correspondientes a una lista de contests
     */
    static public Find findTemplateContests(List<Contest> contests) {
        List<ObjectId> contestObjectIds = new ArrayList<>(contests.size());
        for (Contest contest: contests) {
            contestObjectIds.add(contest.templateContestId);
        }
        return findObjectIds(templateContests(), "_id", contestObjectIds);
    }

    /**
     *  Query de la lista de Template Match Events correspondientes a una lista de template contests
     */
    static public Find findTemplateMatchEvents(List<TemplateContest> templateContests) {
        List<ObjectId> templateContestObjectIds = new ArrayList<>(templateContests.size());
        for (TemplateContest templateContest: templateContests) {
            templateContestObjectIds.addAll(templateContest.templateMatchEventIds);
        }
        return findObjectIds(templateMatchEvents(), "_id", templateContestObjectIds);
    }

    /**
     * Query de una lista de ObjectIds (en una misma query)
     *
     * @param collection: MongoCollection a la que hacer la query
     * @param fieldId:    Identificador del campo a buscar
     * @param idList:     Lista de ObjectId (de mongoDb)
     * @return Find (de jongo)
     */
    public static Find findObjectIds(MongoCollection collection, String fieldId, List<ObjectId> idList) {
        // Jongo necesita que le proporcionemos el patrón de "#, #, #" (según el número de parámetros)
        String patternParams = "";
        for (ObjectId id : idList) {
            if (patternParams != "") patternParams += ",";
            patternParams += "#";
        }

        // Componer la query según el número de parámetros
        String pattern = String.format("{%s: {$in: [%s]}}", fieldId, patternParams);
        return collection.find(pattern, idList.toArray());
    }

    public static Find findFields(MongoCollection collection, String fieldId, List<String> fieldList) {
        // Jongo necesita que le proporcionemos el patrón de "#, #, #" (según el número de parámetros)
        String patternParams = "";
        for (String field : fieldList) {
            if (patternParams != "") patternParams += ",";
            patternParams += "#";
        }

        // Componer la query según el número de parámetros
        String pattern = String.format("{%s: {$in: [%s]}}", fieldId, patternParams);
        return collection.find(pattern, fieldList.toArray());
    }

    public static Iterable<TemplateContest> findTemplateContestsFromIds(String fieldId, List<ObjectId> idList) {
        return findObjectIds(templateContests(), fieldId, idList).as(TemplateContest.class);
    }

    public static Iterable<TemplateMatchEvent> findTemplateMatchEventFromIds(String fieldId, List<ObjectId> idList) {
        return findObjectIds(templateMatchEvents(), fieldId, idList).as(TemplateMatchEvent.class);
    }

    public static Iterable<TemplateSoccerPlayer> findTemplateSoccerPlayersFromIds(String fieldId, List<ObjectId> idList) {
        return findObjectIds(templateSoccerPlayers(), fieldId, idList).as(TemplateSoccerPlayer.class);
    }

    public static Iterable<LiveMatchEvent> findLiveMatchEventsFromIds(String fieldId, List<ObjectId> idList) {
        return findObjectIds(liveMatchEvents(), fieldId, idList).as(LiveMatchEvent.class);
    }

    public static List<SoccerPlayer> getSoccerPlayersInContestEntry(String contestEntryId) {
        ContestEntry contestEntry = contestEntry(new ObjectId(contestEntryId));
        Contest contest = contest(contestEntry.contestId);
        TemplateContest templateContest = templateContest(contest.templateContestId);
        List<ObjectId> templateMatchEventIds = templateContest.templateMatchEventIds;

        //Iterable<LiveMatchEvent> liveMatchEventsResults = Model.liveMatchEvents().find().as(LiveMatchEvent.class);
        Iterable<LiveMatchEvent> liveMatchEventsResults = Model.findObjectIds(Model.liveMatchEvents(), "templateMatchEventId", templateMatchEventIds).as(LiveMatchEvent.class);
        List<LiveMatchEvent> liveMatchEventList = ListUtils.asList(liveMatchEventsResults);

        List<SoccerPlayer> soccerPlayers = new ArrayList<>();
        for (ObjectId soccerId : contestEntry.soccerIds) {
            for (LiveMatchEvent liveMatchEvent : liveMatchEventList) {
                SoccerPlayer liveSoccer = liveMatchEvent.findTemplateSoccerPlayer(soccerId);
                if (liveSoccer != null) {
                    soccerPlayers.add(liveSoccer);
                    break;
                }
            }
        }
        return soccerPlayers;
    }

    /**
     * Elimina el caracter inicial del identificador incluido por Opta (de existir)
     * @param optaId
     * @return
     */
    public static String getPlayerIdFromOpta(String optaId) {
        return (optaId.charAt(0) == 'p') ? optaId.substring(1) : optaId;
    }

    public static String getTeamIdFromOpta(String optaId) {
        return (optaId.charAt(0) == 't') ? optaId.substring(1) : optaId;
    }

    public static String getMatchEventIdFromOpta(String optaId) {
        return (optaId.charAt(0) == 'g') ? optaId.substring(1) : optaId;
    }

    /**
     *  Estado del partido
     */
    public static boolean isMatchEventStarted(TemplateMatchEvent templateMatchEvent) {
        String optaMatchEventId = getMatchEventIdFromOpta(templateMatchEvent.optaMatchEventId);

        // Inicio del partido?
        OptaEvent optaEvent = optaEvents().findOne("{gameId: #, typeId: 32, periodId: 1}", optaMatchEventId).as(OptaEvent.class);
        if (optaEvent == null) {
            // Kick Off Pass?
            optaEvent = optaEvents().findOne("{gameId: #, typeId: 1, periodId: 1, qualifiers: 278}", optaMatchEventId).as(OptaEvent.class);
        }

        /*
        Logger.info("isStarted? {}({}) = {}",
                templateMatchEvent.soccerTeamA.name + " vs " + templateMatchEvent.soccerTeamB.name, templateMatchEvent.optaMatchEventId, (optaEvent!= null));
        */
        return (optaEvent != null);
    }

    public static boolean isMatchEventStarted(String templateMatchEventId) {
        return isMatchEventStarted(templateMatchEvent(new ObjectId(templateMatchEventId)));
    }

    public static boolean isMatchEventFinished(TemplateMatchEvent templateMatchEvent) {
        String optaMatchEventId = getMatchEventIdFromOpta(templateMatchEvent.optaMatchEventId);

        OptaEvent optaEvent = optaEvents().findOne("{gameId: #, typeId: 30, periodId: 14}", optaMatchEventId).as(OptaEvent.class);

        /*
        Logger.info("isFinished? {}({}) = {}",
                templateMatchEvent.soccerTeamA.name + " vs " + templateMatchEvent.soccerTeamB.name, templateMatchEvent.optaMatchEventId, (optaEvent!= null));
        */
        return (optaEvent != null);
    }

    public static boolean isMatchEventFinished(String templateMatchEventId) {
        return isMatchEventFinished(templateMatchEvent(new ObjectId(templateMatchEventId)));
    }

    /**
     * Buscar el tiempo actual del partido
     * @param templateMatchEventId
     * @return Tiempo transcurrido
     */
    public static Date matchEventTime(String templateMatchEventId) {
        TemplateMatchEvent templateMatchEvent = templateMatchEvent(new ObjectId(templateMatchEventId));
        Date dateNow = templateMatchEvent.startDate;

        String optaMatchEventId = getMatchEventIdFromOpta(templateMatchEvent.optaMatchEventId);

        // Buscar el ultimo evento registrado por el partido
        Iterable<OptaEvent> optaEvents = optaEvents().find("{gameId: #}", optaMatchEventId).sort("{timestamp: -1}").limit(1).as(OptaEvent.class);
        if (optaEvents.iterator().hasNext()) {
            OptaEvent event = optaEvents.iterator().next();
            dateNow = event.timestamp;
            Logger.info("matchEventTime from optaEvent: gameId({}) id({})", optaMatchEventId, event.eventId);
        }

        Logger.info("matchEventTime ({}): {}", templateMatchEvent.optaMatchEventId, dateNow);
        return dateNow;
    }

    /**
     * Obtener un elemento aleatorio de una coleccion de MongoDB
     * IMPORTANTE: Muy lento
     * @param collection MongoCollection de la que obtener el elemento
     * @return Un elemento aleatorio
     */
    public static Find getRandomDocument(MongoCollection collection) {
        long count = collection.count();
        int rand = (int) Math.floor(Math.random() * count);
        return collection.find().limit(1).skip(rand);
    }

    // http://docs.mongodb.org/ecosystem/tutorial/getting-started-with-java-driver/
    static private MongoClient _mongoClient;

    // From http://docs.mongodb.org/ecosystem/drivers/java-concurrency/
    // DB and DBCollection are completely thread safe. In fact, they are cached so you get the same instance no matter what.
    static private DB _mongoDB;

    // Jongo is thread safe too: https://groups.google.com/forum/#!topic/jongo-user/KwukXi5Vm7c
    static private Jongo _jongo;
}
