package model;

import com.mongodb.*;
import org.jongo.Jongo;
import org.jongo.MongoCollection;
import play.Logger;
import play.Play;


public class Model {

    static public DB mongoDB() { return _mongoDB; }
    static public Jongo jongo() { return _jongo; }
    static public MongoCollection sessions() { return _jongo.getCollection("sessions"); }
    static public MongoCollection users() { return _jongo.getCollection("users"); }

    static public MongoCollection templateContests() { return _jongo.getCollection("templateContests"); }
    static public MongoCollection templateMatchEvents() { return _jongo.getCollection("templateMatchEvents"); }
    static public MongoCollection templateSoccerTeams() { return _jongo.getCollection("templateSoccerTeams"); }
    static public MongoCollection templateSoccerPlayers() { return _jongo.getCollection("templateSoccerPlayers"); }

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
            "matchEvents"
    };

    static private void clearContestsDB(DB theMongoDB) {
        for(String name : contestCollectionNames) {
            //Logger.info("{} drop", name);
            theMongoDB.getCollection(name).drop();
        }
    }

    static private void ensureContestsDB(DB theMongoDB) {
        for(String name : contestCollectionNames) {
            //Logger.info("{} created", name);
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
        DBCollection optaPlayers = theMongoDB.getCollection("optaPlayers");
        DBCollection optaTeams = theMongoDB.getCollection("optaTeams");
        DBCollection pointsTranslation = theMongoDB.getCollection("pointsTranslation");
        DBCollection fantasyPoints = theMongoDB.getCollection("fantasyPoints");

        //ensureContestsDB(theMongoDB);
    }

    static public void resetDB() {
        if (Play.isProd())
            return;

        _mongoDB.dropDatabase();
        ensureDB(_mongoDB);

        // During development we like to always have test data
        MockData.ensureDBMockData();
    }

    static public void resetContests() {
        if (Play.isProd())
            return;

        clearContestsDB(_mongoDB);
        ensureContestsDB(_mongoDB);

        // During development we like to always have test data
        // MockData.ensureDBMockData();
    }

    // http://docs.mongodb.org/ecosystem/tutorial/getting-started-with-java-driver/
    static private MongoClient _mongoClient;

    // From http://docs.mongodb.org/ecosystem/drivers/java-concurrency/
    // DB and DBCollection are completely thread safe. In fact, they are cached so you get the same instance no matter what.
    static private DB _mongoDB;

    // Jongo is thread safe too: https://groups.google.com/forum/#!topic/jongo-user/KwukXi5Vm7c
    static private Jongo _jongo;
}
