package model;

import com.mongodb.*;
import org.bson.types.ObjectId;
import org.jongo.Find;
import org.jongo.Jongo;
import org.jongo.MongoCollection;
import play.Logger;
import play.Play;
import utils.ListUtils;

import java.sql.*;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;


public class Model {
    static public DB mongoDB() { return _mongoDB; }
    static public DB mongoDBAdmin() { return _mongoDBAdmin; }
    static public DB mongoDBSnapshot() { return _mongoDBSnapshot; }

    static public Jongo jongo() { return _jongo; }
    static public Jongo jongoSnapshot() { return _jongoSnapshot; }
    static public MongoCollection sessions() { return _jongo.getCollection("sessions"); }
    static public MongoCollection users() { return _jongo.getCollection("users"); }

    static public MongoCollection templateContests() { return _jongo.getCollection("templateContests"); }
    static public MongoCollection templateMatchEvents() { return _jongo.getCollection("templateMatchEvents"); }
    static public MongoCollection templateSoccerTeams() { return _jongo.getCollection("templateSoccerTeams"); }
    static public MongoCollection templateSoccerPlayers() { return _jongo.getCollection("templateSoccerPlayers"); }
    static public MongoCollection templateSoccerPlayersMetadata() { return _jongo.getCollection("templateSoccerPlayersMetadata"); }
    static public MongoCollection contestEntries() { return _jongo.getCollection("contestEntries"); }

    static public MongoCollection contests() { return _jongo.getCollection("contests"); }

    static public MongoCollection optaDB() { return _jongo.getCollection("optaDB"); }
    static public MongoCollection optaEvents() { return _jongo.getCollection("optaEvents"); }
    static public MongoCollection optaPlayers() { return _jongo.getCollection("optaPlayers"); }
    static public MongoCollection optaTeams() { return _jongo.getCollection("optaTeams"); }
    static public MongoCollection optaMatchEvents() { return _jongo.getCollection("optaMatchEvents"); }
    static public MongoCollection pointsTranslation() { return _jongo.getCollection("pointsTranslation"); }

    static public void init() {
        if (Play.isTest())
            return;

        String mongodbUri = Play.application().configuration().getString("mongodb.uri");
        MongoClientURI mongoClientURI = new MongoClientURI(mongodbUri);

        Logger.info("The MongoDB is {}/{}", mongoClientURI.getHosts(), mongoClientURI.getDatabase());

        try {
            _mongoClient = new MongoClient(mongoClientURI);
            _mongoDB = _mongoClient.getDB(mongoClientURI.getDatabase());
            _mongoDBAdmin = _mongoClient.getDB("admin");
            _jongo = new Jongo(_mongoDB);

            if (!Play.isProd()) {
                _mongoDBSnapshot = _mongoClient.getDB("snapshot");
                _jongoSnapshot = new Jongo(mongoDBSnapshot());
            } else {
                _mongoDBSnapshot = null;
                _jongoSnapshot = null;
            }

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
                             " created_at timestamp, " +
                             " name text, " +
                             " feed_type text, " +
                             " game_id text, " +
                             " competition_id text, " +
                             " season_id text, " +
                             " last_updated timestamp " +
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

    static public void dropSnapshotDB() {
        dropDB(_mongoDBSnapshot);
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
        /*
        for (String name : allCollectionNames) {
            theMongoDB.getCollection(name).drop();
        }
        */
        theMongoDB.dropDatabase();
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

        if (!theMongoDB.collectionExists("templateSoccerPlayersMetadata"))
            theMongoDB.createCollection("templateSoccerPlayersMetadata", new BasicDBObject());

        if (!theMongoDB.collectionExists("contests"))
            theMongoDB.createCollection("contests", new BasicDBObject());

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

    /**
     * Igual que la anterior pero añadiendo un filtro
     */
    public static Find findObjectIds(MongoCollection collection, String fieldId, String filter, Iterable<ObjectId> objectIdsIterable) {
        return collection.find(String.format("{%s: {$in: #}, %s}", fieldId, filter), ListUtils.asList(objectIdsIterable));
    }

    public static void insertXML(String xml, String headers, Date timestamp, String name, String feedType,
                                 String gameId, String competitionId, String seasonId, Date lastUpdated) {

        String insertString = "INSERT INTO optaxml (xml, headers, created_at, name, feed_type, game_id, competition_id," +
                              "season_id, last_updated) VALUES (?,?,?,?,?,?,?,?,?)";

        try (Connection connection = play.db.DB.getConnection()) {
            try (PreparedStatement stmt = connection.prepareStatement(insertString)) {
                stmt.setString(1, xml);
                stmt.setString(2, headers);
                if (timestamp != null) {
                    stmt.setTimestamp(3, new java.sql.Timestamp(timestamp.getTime()));
                } else {
                    stmt.setTimestamp(3, null);
                }
                stmt.setString(4, name);
                stmt.setString(5, feedType);
                stmt.setString(6, gameId);
                stmt.setString(7, competitionId);
                stmt.setString(8, seasonId);

                if (lastUpdated != null) {
                    stmt.setTimestamp(9, new java.sql.Timestamp(lastUpdated.getTime()));
                } else {
                    stmt.setTimestamp(9, null);
                }

                if (stmt.execute()) {
                    Logger.info("Successful insert in OptaXML");
                }
            }
        }
        catch (java.sql.SQLException e) {
            Logger.error("WTF 56312: ", e);
        }
    }

    public static Date getDateFromHeader(String dateStr) {
        if (dateStr == null) {
            return null;
        }
        DateFormat formatter;
        if (dateStr.indexOf('-')>=0) {
            if (dateStr.indexOf('T')>=0) {
                formatter = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS");
            } else {
                formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.ENGLISH);
            }
        } else {
           formatter = new SimpleDateFormat("E MMM dd HH:mm:ss Z yyyy", Locale.ENGLISH);
        }
        formatter.setTimeZone(TimeZone.getTimeZone("UTC"));
        Date date = null;
        try {
            date = (Date)formatter.parse(dateStr);
        } catch (ParseException e) {
            Logger.error("WTF 23815 Data parsing: ", e);
        }
        Logger.debug(date.toString());
        return date;
    }

    public static Date dateFirstFromOptaXML() {
        Date dateFirst = new Date(0L);
        try (Connection connection = play.db.DB.getConnection()) {
            String selectString = "SELECT created_at FROM optaxml ORDER BY created_at ASC LIMIT 1;";

            Statement stmt = connection.createStatement(ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY);
            ResultSet resultSet = stmt.executeQuery(selectString);
            if (resultSet != null && resultSet.next()) {
                dateFirst = resultSet.getTimestamp("created_at");
            }
        }
        catch (java.sql.SQLException e) {
            Logger.error("WTF 82847", e);
        }
        return dateFirst;
    }

    public static Date dateLastFromOptaXML() {
        Date dateLast = new Date(0L);
        try (Connection connection = play.db.DB.getConnection()) {
            String selectString = "SELECT created_at FROM optaxml ORDER BY created_at DESC LIMIT 1;";

            Statement stmt = connection.createStatement(ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY);
            ResultSet resultSet = stmt.executeQuery(selectString);
            if (resultSet != null && resultSet.next()) {
                dateLast = resultSet.getTimestamp("created_at");
            }
        }
        catch (java.sql.SQLException e) {
            Logger.error("WTF 82848", e);
        }
        return dateLast;
    }

    // http://docs.mongodb.org/ecosystem/tutorial/getting-started-with-java-driver/
    static private MongoClient _mongoClient;

    // From http://docs.mongodb.org/ecosystem/drivers/java-concurrency/
    // DB and DBCollection are completely thread safe. In fact, they are cached so you get the same instance no matter what.
    static private DB _mongoDB;

    static private DB _mongoDBAdmin;
    static private DB _mongoDBSnapshot;

    // Jongo is thread safe too: https://groups.google.com/forum/#!topic/jongo-user/KwukXi5Vm7c
    static private Jongo _jongo;
    static private Jongo _jongoSnapshot;
}
