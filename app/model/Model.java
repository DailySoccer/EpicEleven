package model;

import com.mongodb.*;
import org.joda.time.DateTime;
import org.jongo.Jongo;
import play.Logger;
import play.Play;
import utils.*;

import java.security.SecureRandom;
import java.util.Date;

public class Model {

    // http://jongo.org/
    static public Jongo createJongo() {
        return new Jongo(_mongoDB);
    }


    static public void init() {
        String mongodbUri = Play.application().configuration().getString("mongodb.uri");
        MongoClientURI mongoClientURI = new MongoClientURI(mongodbUri);

        Logger.info("The MongoDB uri is {}", mongodbUri);

        try {
            _mongoClient = new MongoClient(mongoClientURI);
            _mongoDB = _mongoClient.getDB(mongoClientURI.getDatabase());

            // Let's make sure our DB has the neccesary collections and indexes
            ensureDB(_mongoDB);

        } catch (Exception exc) {
            Logger.error("Error initializating MongoDB {}\n{}", mongodbUri, exc.toString());
        }

        _secureRandom = new SecureRandom(DateTime.now().toString().getBytes());
    }


    static public void shutdown() {
        if (_mongoClient != null)
            _mongoClient.close();
    }


    static public String getRandomSessionToken() {
        // Note: Depending on the implementation, the generateSeed and nextBytes methods may block as entropy is being
        // gathered, for example, if they need to read from /dev/random on various unix-like operating systems.
        byte[] nextBytes = new byte[16];
        _secureRandom.nextBytes(nextBytes);
        return StringUtils.bytesToHex(nextBytes);
    }

    static public void ensureDB(DB theMongoDB) {
        DBCollection users = theMongoDB.getCollection("users");

        // Si creando nuevo indice sobre datos que ya existan => .append("dropDups", true)
        users.createIndex(new BasicDBObject("email", 1), new BasicDBObject("unique", true));
        users.createIndex(new BasicDBObject("nickName", 1), new BasicDBObject("unique", true));

        DBCollection sessions = theMongoDB.getCollection("sessions");
        sessions.createIndex(new BasicDBObject("sessionToken", 1), new BasicDBObject("unique", true));
    }


    // http://docs.mongodb.org/ecosystem/tutorial/getting-started-with-java-driver/
    static private MongoClient _mongoClient;

    // From http://docs.mongodb.org/ecosystem/drivers/java-concurrency/
    // DB and DBCollection are completely thread safe. In fact, they are cached so you get the same instance no matter what.
    static private DB _mongoDB;

    // http://docs.oracle.com/javase/7/docs/technotes/guides/security/StandardNames.html#SecureRandom
    static private SecureRandom _secureRandom;
}
