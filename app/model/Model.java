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

    static public MongoCollection prefabContests() { return _jongo.getCollection("prefabContests"); }
    static public MongoCollection prefabMatchEvents() { return _jongo.getCollection("prefabMatchEvents"); }
    static public MongoCollection prefabSoccerTeams() { return _jongo.getCollection("prefabSoccerTeams"); }
    static public MongoCollection prefabSoccerPlayers() { return _jongo.getCollection("prefabSoccerPlayers"); }

    static public MongoCollection contests() { return _jongo.getCollection("contests"); }
    static public MongoCollection matchEvents() { return _jongo.getCollection("matchEvents"); }

    static public MongoCollection xmlcontests() { return _jongo.getCollection("xmlcontests"); }

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


    static public void ensureDB(DB theMongoDB) {
        DBCollection users = theMongoDB.getCollection("users");

        // Si creando nuevo indice sobre datos que ya existan => .append("dropDups", true)
        users.createIndex(new BasicDBObject("email", 1), new BasicDBObject("unique", true));
        users.createIndex(new BasicDBObject("nickName", 1), new BasicDBObject("unique", true));

        // Do we need the sessionToken to be unique? SecureRandom guarantees it to be unique, doesn't it?
        // http://www.kodyaz.com/images/pics/random-number-generator-dilbert-comic.jpg
        DBCollection sessions = theMongoDB.getCollection("sessions");
        sessions.createIndex(new BasicDBObject("sessionToken", 1), new BasicDBObject("unique", true));

        DBCollection prefabContests = theMongoDB.getCollection("prefabContests");
        DBCollection prefabMatchEvents = theMongoDB.getCollection("prefabMatchEvents");
        DBCollection prefabSoccerTeams = theMongoDB.getCollection("prefabSoccerTeams");
        DBCollection prefabSoccerPlayers = theMongoDB.getCollection("prefabSoccerPlayers");

        DBCollection contests = theMongoDB.getCollection("contests");
        DBCollection matchEvents = theMongoDB.getCollection("matchEvents");

        // During development we like to always have test data
        MockData.ensureDBMockData();
    }

    static public void resetDB() {
        if (Play.isProd())
            return;

        _mongoDB.dropDatabase();
        ensureDB(_mongoDB);
    }

    // http://docs.mongodb.org/ecosystem/tutorial/getting-started-with-java-driver/
    static private MongoClient _mongoClient;

    // From http://docs.mongodb.org/ecosystem/drivers/java-concurrency/
    // DB and DBCollection are completely thread safe. In fact, they are cached so you get the same instance no matter what.
    static private DB _mongoDB;

    // Jongo is thread safe too: https://groups.google.com/forum/#!topic/jongo-user/KwukXi5Vm7c
    static private Jongo _jongo;
}
