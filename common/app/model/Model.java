package model;

import com.mongodb.*;
import org.bson.types.ObjectId;
import org.jongo.Find;
import org.jongo.Jongo;
import org.jongo.MongoCollection;
import play.Logger;
import play.Play;
import utils.ListUtils;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;


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
                WaitSeconds(10, "Trying to initialize MongoDB again");
            }
        }

        DataSource ds = play.db.DB.getDataSource();
        java.sql.Connection connection = play.db.DB.getConnection();

        try {
            try (Statement stmt = connection.createStatement()) {
                boolean result = stmt.execute("CREATE TABLE IF NOT EXISTS optaxml (" +
                                              " id serial PRIMARY KEY, " +
                                              " xml xml, " +
                                              " headers text, " +
                                              " created_at timestamp, " +
                                              " name text, " +
                                              " feed_type text, " +
                                              " game_id text, " +
                                              " competition_id text, " +
                                              " season_id text, " +
                                              " last_updated timestamp " +
                                              " );");
                if (result) {
                    Logger.info("Tabla OptaXML creada");
                }
            }
        }
        catch (SQLException e) {
            Logger.error("SQL Exception creating DailySoccerDB table", e);
        }
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

    static public void resetDB() {
        dropDB(_mongoDB);
        ensureDB(_mongoDB);
    }

    static private String[] allCollectionNames = {
            "users",
            "sessions",

            "templateContests",
            "templateMatchEvents",
            "templateSoccerTeams",
            "templateSoccerPlayers",
            "contests",
            "matchEvents",
            "liveMatchEvents",
            "contestEntries",
            "pointsTranslation",

            "optaEvents",
            "optaPlayers",
            "optaTeams",
            "optaMatchEvents"
    };

    static private void dropDB(DB theMongoDB) {
        for (String name : allCollectionNames) {
            theMongoDB.getCollection(name).drop();
        }
    }

    static private void ensureDB(DB theMongoDB) {
        ensureUsersDB(theMongoDB);
        ensureOptaDB(theMongoDB);
        ensureContestsDB(theMongoDB);
    }

    static private void ensureUsersDB(DB theMongoDB) {
        DBCollection users = theMongoDB.getCollection("users");

        // Si creando nuevo indice sobre datos que ya existan => .append("dropDups", true)
        users.createIndex(new BasicDBObject("email", 1), new BasicDBObject("unique", true));
        users.createIndex(new BasicDBObject("nickName", 1), new BasicDBObject("unique", true));

        // Do we need the sessionToken to be unique? SecureRandom guarantees it to be unique, doesn't it?
        // http://www.kodyaz.com/images/pics/random-number-generator-dilbert-comic.jpg
        DBCollection sessions = theMongoDB.getCollection("sessions");
        sessions.createIndex(new BasicDBObject("sessionToken", 1), new BasicDBObject("unique", true));
    }

    private static void ensureOptaDB(DB theMongoDB) {

        DBCollection optaEvents = theMongoDB.getCollection("optaEvents");
        optaEvents.createIndex(new BasicDBObject("parentId", 1));
        optaEvents.createIndex(new BasicDBObject("eventId", 1));
        optaEvents.createIndex(new BasicDBObject("gameId", 1));
        optaEvents.createIndex(new BasicDBObject("optaPlayerId", 1));

        DBCollection optaPlayers = theMongoDB.getCollection("optaPlayers");
        optaPlayers.createIndex(new BasicDBObject("optaPlayerId", 1));

        DBCollection optaTeams = theMongoDB.getCollection("optaTeams");
        DBCollection optaMatchEvents = theMongoDB.getCollection("optaMatchEvents");

        DBCollection pointsTranslation = theMongoDB.getCollection("pointsTranslation");
        pointsTranslation.createIndex(new BasicDBObject("eventTypeId", 1));
    }

    static private void ensureContestsDB(DB theMongoDB) {

        // TODO: Creacion de indexes
        if (!theMongoDB.collectionExists("templateContests"))
            theMongoDB.createCollection("templateContests", new BasicDBObject());

        if (!theMongoDB.collectionExists("templateMatchEvents"))
            theMongoDB.createCollection("templateMatchEvents", new BasicDBObject());

        if (!theMongoDB.collectionExists("templateSoccerTeams"))
            theMongoDB.createCollection("templateSoccerTeams", new BasicDBObject());

        if (!theMongoDB.collectionExists("templateSoccerPlayers"))
            theMongoDB.createCollection("templateSoccerPlayers", new BasicDBObject());

        if (!theMongoDB.collectionExists("contests"))
            theMongoDB.createCollection("contests", new BasicDBObject());

        // TODO: Podemos quitar esto?
        if (!theMongoDB.collectionExists("matchEvents"))
            theMongoDB.createCollection("matchEvents", new BasicDBObject());

        if (!theMongoDB.collectionExists("liveMatchEvents"))
            theMongoDB.createCollection("liveMatchEvents", new BasicDBObject());

        if (!theMongoDB.collectionExists("contestEntries"))
            theMongoDB.createCollection("contestEntries", new BasicDBObject());
    }


    /**
     * Query de una lista de ObjectIds (en una misma query)
     *
     * @param collection: MongoCollection a la que hacer la query
     * @param fieldId:    Identificador del campo a buscar (p ej, 'templateContestId')
     * @param objectIdsIterable: Lista de ObjectId (de mongoDb)
     */
    public static Find findObjectIds(MongoCollection collection, String fieldId, Iterable<ObjectId> objectIdsIterable) {
        return collection.find(String.format("{%s: {$in: #}}", fieldId), ListUtils.asList(objectIdsIterable));
    }


    // http://docs.mongodb.org/ecosystem/tutorial/getting-started-with-java-driver/
    static private MongoClient _mongoClient;

    // From http://docs.mongodb.org/ecosystem/drivers/java-concurrency/
    // DB and DBCollection are completely thread safe. In fact, they are cached so you get the same instance no matter what.
    static private DB _mongoDB;

    // Jongo is thread safe too: https://groups.google.com/forum/#!topic/jongo-user/KwukXi5Vm7c
    static private Jongo _jongo;
}
