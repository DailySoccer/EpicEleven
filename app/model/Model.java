package model;

import com.mongodb.*;
import org.jongo.Jongo;
import org.jongo.MongoCollection;
import play.Logger;
import play.Play;
import org.bson.types.ObjectId;

import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;
import java.util.Iterator;
import model.opta.*;
import play.mvc.Result;
import utils.ListUtils;


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
    static public MongoCollection fantasyTeams() { return _jongo.getCollection("fantasyTeams"); }
    static public MongoCollection contestEntries() { return _jongo.getCollection("contestEntries"); }

    static public MongoCollection contests() { return _jongo.getCollection("contests"); }
    static public MongoCollection matchEvents() { return _jongo.getCollection("matchEvents"); }

    static public MongoCollection optaDB() { return _jongo.getCollection("optaDB"); }
    static public MongoCollection optaEvents() { return _jongo.getCollection("optaEvents"); }
    static public MongoCollection optaPlayers() { return _jongo.getCollection("optaPlayers"); }
    static public MongoCollection optaTeams() { return _jongo.getCollection("optaTeams"); }
    static public MongoCollection pointsTranslation() { return _jongo.getCollection("pointsTranslation"); }
    static public MongoCollection fantasyPoints() { return _jongo.getCollection("fantasyPoints"); }

    static public void init() {
        String mongodbUri = Play.application().configuration().getString("mongodb.uri");
        MongoClientURI mongoClientURI = new MongoClientURI(mongodbUri);

        Logger.info("The MongoDB uri is {}", mongodbUri);

        boolean bIsInitialized = false;
        while (!bIsInitialized) {
            try {
                _mongoClient = new MongoClient(mongoClientURI);
                _mongoDB = _mongoClient.getDB(mongoClientURI.getDatabase());
                _jongo = new Jongo(_mongoDB);

                // Let's make sure our DB has the neccesary collections and indexes
                ensureDB(_mongoDB);

                bIsInitialized = true;

            } catch (Exception exc) {
                Logger.error("Error initializating MongoDB {}: {}", mongodbUri, exc.toString());

                // We try again in 10s
                try {
                    Logger.info("Trying to initialize MongoDB again in 10s...");
                    Thread.sleep(10000);
                } catch (InterruptedException intExc) { Logger.error("Interrupted"); }
            }
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
            "fantasyTeams",
            "contestEntries"
    };

    static private void clearContestsDB(DB theMongoDB) {
        for(String name : contestCollectionNames) {
            theMongoDB.getCollection(name).drop();
        }
    }

    static private void ensureContestsDB(DB theMongoDB) {
        for(String name : contestCollectionNames) {
            if (!theMongoDB.collectionExists(name))
                theMongoDB.createCollection(name, new BasicDBObject());
        }
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
        DBCollection optaEvents = theMongoDB.getCollection("optaEvents");
        optaEvents.createIndex(new BasicDBObject("parentId", 1));
        optaEvents.createIndex(new BasicDBObject("eventId", 1));
        optaEvents.createIndex(new BasicDBObject("gameId", 1));
        optaEvents.createIndex(new BasicDBObject("playerId", 1));
        DBCollection optaPlayers = theMongoDB.getCollection("optaPlayers");
        DBCollection optaTeams = theMongoDB.getCollection("optaTeams");
        DBCollection pointsTranslation = theMongoDB.getCollection("pointsTranslation");
        pointsTranslation.createIndex(new BasicDBObject("eventCode", 1));
        DBCollection fantasyPoints = theMongoDB.getCollection("fantasyPoints");
        fantasyPoints.createIndex(new BasicDBObject("playerId", 1));

        ensureContestsDB(theMongoDB);
    }

    static public void resetDB() {
        _mongoDB.dropDatabase();
        ensureDB(_mongoDB);
    }

    static public void resetContests() {
        clearContestsDB(_mongoDB);
        ensureContestsDB(_mongoDB);
    }

    static public void importTeamsAndSoccersFromOptaDB() {
        long startTime = System.currentTimeMillis();

        HashMap<String, TemplateSoccerTeam> teamsMap = new HashMap<>();
        Iterable<OptaTeam> optaTeams = Model.optaTeams().find().as(OptaTeam.class);
        for (OptaTeam optaTeam: optaTeams) {
            TemplateSoccerTeam templateTeam = new TemplateSoccerTeam(optaTeam);
            templateTeam.templateSoccerTeamId = new ObjectId();
            Model.templateSoccerTeams().withWriteConcern(WriteConcern.SAFE).insert(templateTeam);

            teamsMap.put(templateTeam.optaTeamId,  templateTeam);
        }

        Iterable<OptaPlayer> optaPlayers = Model.optaPlayers().find().as(OptaPlayer.class);
        for (OptaPlayer optaPlayer: optaPlayers) {
            ObjectId teamId = null;
            if (teamsMap.containsKey(optaPlayer.teamId)) {
                teamId = teamsMap.get(optaPlayer.teamId).templateSoccerTeamId;

                TemplateSoccerPlayer templatePlayer = new TemplateSoccerPlayer(optaPlayer, teamId);
                Model.templateSoccerPlayers().withWriteConcern(WriteConcern.SAFE).insert(templatePlayer);
            }
        }

        Logger.info("import Teams&Soccers: {}", System.currentTimeMillis() - startTime);
    }

    static public void setLiveFantasyPointsOfSoccerPlayer(ObjectId soccerPlayerId, String strPoints) {
        Logger.info("setLiveFantasyPoints: {} = {} fantasy points", soccerPlayerId, strPoints);

        long startTime = System.currentTimeMillis();

        // Actualizar jugador si aparece en TeamA
        Model.liveMatchEvents()
                .update("{soccerTeamA.soccerPlayers.templateSoccerPlayerId: #}", soccerPlayerId)
                .multi()
                .with("{$set: {soccerTeamA.soccerPlayers.$.fantasyPoints: #}}", strPoints);

        // Actualizar jugador si aparece en TeamB
        Model.liveMatchEvents()
                .update("{soccerTeamB.soccerPlayers.templateSoccerPlayerId: #}", soccerPlayerId)
                .multi()
                .with("{$set: {soccerTeamB.soccerPlayers.$.fantasyPoints: #}}", strPoints);

        Logger.info("END: setLiveFantasyPoints: {}", System.currentTimeMillis() - startTime);
    }

    static public void updateLiveFantasyPoints(SoccerPlayer soccerPlayer) {
        // Obtener sus fantasy points actuales
        Iterable<FantasyPoints> fantasyPointResults = fantasyPoints().find("{playerId: #",
                soccerPlayer.optaPlayerId).as(FantasyPoints.class);

        // Sumarlos
        float points = 0;
        for (FantasyPoints point: fantasyPointResults) {
            points = point.points;
        }

        // Actualizar sus puntos en cada LiverMatchEvent en el que participe
        setLiveFantasyPointsOfSoccerPlayer(soccerPlayer.templateSoccerPlayerId, String.valueOf(points));
    }

    static public void updateLiveFantasyPoints(LiveMatchEvent liveMatchEvent) {
        // Actualizamos los jugadores del TeamA
        for (SoccerPlayer soccer: liveMatchEvent.soccerTeamA.soccerPlayers) {
            updateLiveFantasyPoints(soccer);
        }

        // Actualizamos los jugadores del TeamB
        for (SoccerPlayer soccer: liveMatchEvent.soccerTeamB.soccerPlayers) {
            updateLiveFantasyPoints(soccer);
        }
    }

    static public void updateLiveFantasyPoints(List<String> strMatchEventIdsList) {
        Logger.info("updateLiveFantasyPoints: {}", strMatchEventIdsList);

        long startTime = System.currentTimeMillis();

        Iterable<LiveMatchEvent> liveMatchEventResults = Model.findLiveMatchEventsFromIds("templateMatchEventId", strMatchEventIdsList);
        List<LiveMatchEvent> liveMatchEventList = ListUtils.listFromIterator(liveMatchEventResults.iterator());

        for (LiveMatchEvent liveMatchEvent: liveMatchEventList) {
            updateLiveFantasyPoints(liveMatchEvent);
        }

        Logger.info("END: updateLiveFantasyPoints: {}", System.currentTimeMillis() - startTime);
    }

    static public User findUserId(String userId) {
        User aUser = null;
        Boolean userValid = ObjectId.isValid(userId);
        if (userValid) {
            aUser = Model.users().findOne(new ObjectId(userId)).as(User.class);
        }
        return aUser;
    }

    static public Contest findContestId(String contestId) {
        Contest aContest = null;
        Boolean userValid = ObjectId.isValid(contestId);
        if (userValid) {
            aContest = Model.contests().findOne(new ObjectId(contestId)).as(Contest.class);
        }
        return aContest;
    }

    /**
     * Obtener una lista de Objetos a partir de una lista de 'string ids' (StringId = ObjectID.toString())
     * @param classType: Clase de la lista de objetos a devolver (necesario para usar en la query a jongo)
     * @param collection: MongoCollection a la que hacer la query
     * @param fieldId: Identificador del campo a buscar
     * @param strIdsList: Lista de 'String Ids' (de mongoDb)
     * @return Lista de Objetos correspondientes a los ids incluidos en strIdsList
     */
    public static <T> Iterable<T> findObjectsFromIds(Class<T> classType, MongoCollection collection, String fieldId, List<String> strIdsList) {
        ArrayList<ObjectId> objectIdsList = new ArrayList<>();

        // Jongo necesita que le proporcionemos el patrón de "#, #, #" (según el número de parámetros)
        String patternParams = "";
        for (String id : strIdsList) {
            if (patternParams != "") patternParams += ",";
            patternParams += "#";
            // Convertir un id en formato cadena a ObjectId
            objectIdsList.add(new ObjectId(id));
        }

        // Componer la query según el número de parámetros
        String pattern = String.format("{%s: {$in: [%s]}}", fieldId, patternParams);
        return collection.find(pattern, objectIdsList.toArray()).as(classType);
    }

    public static Iterable<TemplateContest> findTemplateContestsFromIds(String fieldId, List<String> strIdsList) {
        return findObjectsFromIds(TemplateContest.class, Model.templateContests(), fieldId, strIdsList);
    }

    public static Iterable<TemplateMatchEvent> findTemplateMatchEventFromIds(String fieldId, List<String> strIdsList) {
        return findObjectsFromIds(TemplateMatchEvent.class, Model.templateMatchEvents(), fieldId, strIdsList);
    }

    public static Iterable<TemplateSoccerPlayer> findTemplateSoccerPlayersFromIds(String fieldId, List<String> strIdsList) {
        return findObjectsFromIds(TemplateSoccerPlayer.class, Model.templateSoccerPlayers(), fieldId, strIdsList);
    }

    public static Iterable<LiveMatchEvent> findLiveMatchEventsFromIds(String fieldId, List<String> strIdsList) {
        return findObjectsFromIds(LiveMatchEvent.class, Model.liveMatchEvents(), fieldId, strIdsList);
    }

    // http://docs.mongodb.org/ecosystem/tutorial/getting-started-with-java-driver/
    static private MongoClient _mongoClient;

    // From http://docs.mongodb.org/ecosystem/drivers/java-concurrency/
    // DB and DBCollection are completely thread safe. In fact, they are cached so you get the same instance no matter what.
    static private DB _mongoDB;

    // Jongo is thread safe too: https://groups.google.com/forum/#!topic/jongo-user/KwukXi5Vm7c
    static private Jongo _jongo;
}
