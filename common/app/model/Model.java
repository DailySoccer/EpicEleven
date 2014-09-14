package model;

import com.mongodb.*;
import org.bson.types.ObjectId;
import org.jongo.Find;
import org.jongo.Jongo;
import org.jongo.MongoCollection;
import play.Logger;
import play.Play;
import utils.ListUtils;

import java.sql.Connection;
import java.sql.*;
import java.util.Date;
import java.util.List;
import java.util.Set;


public class Model {

    static public MongoCollection sessions() { return _jongo.getCollection("sessions"); }
    static public MongoCollection users() { return _jongo.getCollection("users"); }

    static public MongoCollection templateContests() { return _jongo.getCollection("templateContests"); }
    static public MongoCollection templateMatchEvents() { return _jongo.getCollection("templateMatchEvents"); }
    static public MongoCollection templateSoccerTeams() { return _jongo.getCollection("templateSoccerTeams"); }
    static public MongoCollection templateSoccerPlayers() { return _jongo.getCollection("templateSoccerPlayers"); }
    static public MongoCollection templateSoccerPlayersMetadata() { return _jongo.getCollection("templateSoccerPlayersMetadata"); }

    static public MongoCollection contests() { return _jongo.getCollection("contests"); }
    static public MongoCollection matchEvents() { return _jongo.getCollection("matchEvents"); }

    static public MongoCollection optaCompetitions() { return _jongo.getCollection("optaCompetitions"); }
    static public MongoCollection optaEvents() { return _jongo.getCollection("optaEvents"); }
    static public MongoCollection optaPlayers() { return _jongo.getCollection("optaPlayers"); }
    static public MongoCollection optaTeams() { return _jongo.getCollection("optaTeams"); }
    static public MongoCollection optaMatchEvents() { return _jongo.getCollection("optaMatchEvents"); }
    static public MongoCollection optaMatchEventStats() { return _jongo.getCollection("optaMatchEventStats"); }
    static public MongoCollection pointsTranslation() { return _jongo.getCollection("pointsTranslation"); }
    static public MongoCollection optaProcessor()     { return _jongo.getCollection("optaProcessor"); }

    static public MongoCollection simulator() { return _jongo.getCollection("simulator"); }

    static public void init() {

        String mongodbUri = Play.application().configuration().getString("mongodb.uri");
        MongoClientURI mongoClientURI = new MongoClientURI(mongodbUri);

        Logger.info("The MongoDB is {}/{}", mongoClientURI.getHosts(), mongoClientURI.getDatabase());

        try {
            _mongoClient = new MongoClient(mongoClientURI);
            _mongoDB = _mongoClient.getDB(mongoClientURI.getDatabase());
            _jongo = new Jongo(_mongoDB);

            // Let's make sure our DB has the neccesary collections and indexes
            ensureDB(_mongoDB);
        }
        catch (Exception exc) {
            Logger.error("Error initializating MongoDB {}/{}: {}", mongoClientURI.getHosts(),
                                                                   mongoClientURI.getDatabase(), exc.toString());
        }

        try {
            ensurePostgresDB();
        }
        catch (Exception exc) {
            Logger.error("Error creating optaxml: ", exc);
        }
    }

    private static void ensurePostgresDB() throws SQLException {

        try (Connection connection = play.db.DB.getConnection()) {
            try (Statement stmt = connection.createStatement()) {
                stmt.execute("CREATE TABLE IF NOT EXISTS optaxml (" +
                             " id serial PRIMARY KEY, " +
                             " xml text, " +
                             " headers text, " +
                             " created_at timestamp with time zone, " +
                             " name text, " +
                             " feed_type text, " +
                             " game_id text, " +
                             " competition_id text, " +
                             " season_id text, " +
                             " last_updated timestamp with time zone " +
                             " );");

                // http://dba.stackexchange.com/questions/35616/create-index-if-it-does-not-exist
                stmt.execute("DO $$ " +
                             "BEGIN " +
                                 "IF NOT EXISTS ( " +
                                     "SELECT 1 " +
                                     "FROM  pg_class c " +
                                     "JOIN  pg_namespace n ON n.oid = c.relnamespace " +
                                     "WHERE c.relname = 'created_at_index' " +
                                     "AND   n.nspname = 'public' " +
                                     ") THEN " +
                                     "CREATE INDEX created_at_index ON public.optaxml (created_at); " +
                                 "END IF; " +
                             "END$$;");
            }
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

    static private void dropDB(DB theMongoDB) {

        for (String collection: theMongoDB.getCollectionNames()) {
            if (!collection.contains("system.")) {
                Logger.debug("About to delete collection: {}", collection);
                theMongoDB.getCollection(collection).drop();
            }
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

        DBCollection optaCompetitions = theMongoDB.getCollection("optaCompetitions");
        optaCompetitions.createIndex(new BasicDBObject("competitionId", 1));

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

        if (!theMongoDB.collectionExists("templateContests")) {
            DBCollection templateContests = theMongoDB.createCollection("templateContests", new BasicDBObject());
            templateContests.createIndex(new BasicDBObject("templateMatchEventIds", 1));
            templateContests.createIndex(new BasicDBObject("state", 1));
        }

        if (!theMongoDB.collectionExists("templateMatchEvents")) {
            DBCollection templateMatchEvents = theMongoDB.createCollection("templateMatchEvents", new BasicDBObject());
            templateMatchEvents.createIndex(new BasicDBObject("optaMatchEventId", 1));
        }

        if (!theMongoDB.collectionExists("templateSoccerTeams")) {
            DBCollection templateSoccerTeams = theMongoDB.createCollection("templateSoccerTeams", new BasicDBObject());
            templateSoccerTeams.createIndex(new BasicDBObject("optaTeamId", 1));
        }

        if (!theMongoDB.collectionExists("templateSoccerPlayers")) {
            DBCollection templateSoccerPlayers = theMongoDB.createCollection("templateSoccerPlayers", new BasicDBObject());
            templateSoccerPlayers.createIndex(new BasicDBObject("templateTeamId", 1));
            templateSoccerPlayers.createIndex(new BasicDBObject("optaPlayerId", 1));
        }

        if (!theMongoDB.collectionExists("templateSoccerPlayersMetadata"))
            theMongoDB.createCollection("templateSoccerPlayersMetadata", new BasicDBObject());

        if (!theMongoDB.collectionExists("contests")) {
            DBCollection contests = theMongoDB.createCollection("contests", new BasicDBObject());
            contests.createIndex(new BasicDBObject("templateContestId", 1));
            contests.createIndex(new BasicDBObject("contestEntries._id", 1));
            contests.createIndex(new BasicDBObject("contestEntries.userId", 1));
        }

        if (!theMongoDB.collectionExists("matchEvents")) {
            DBCollection matchEvents = theMongoDB.createCollection("matchEvents", new BasicDBObject());
            matchEvents.createIndex(new BasicDBObject("templateMatchEventId", 1));
            matchEvents.createIndex(new BasicDBObject("optaMatchEventId", 1));
        }
    }


    /**
     * Query de una lista de ObjectIds (en una misma query)
     *
     * @param collection: MongoCollection a la que hacer la query
     * @param fieldId:    Identificador del campo a buscar (p ej, 'templateContestId')
     * @param objectIds: Lista de ObjectId (de mongoDb)
     */
    public static Find findObjectIds(MongoCollection collection, String fieldId, List<ObjectId> objectIds) {
        return collection.find(String.format("{%s: {$in: #}}", fieldId), objectIds);
    }


    // http://docs.mongodb.org/ecosystem/tutorial/getting-started-with-java-driver/
    static private MongoClient _mongoClient;

    // From http://docs.mongodb.org/ecosystem/drivers/java-concurrency/
    // DB and DBCollection are completely thread safe. In fact, they are cached so you get the same instance no matter what.
    static private DB _mongoDB;

    // Jongo is thread safe too: https://groups.google.com/forum/#!topic/jongo-user/KwukXi5Vm7c
    static private Jongo _jongo;
}
