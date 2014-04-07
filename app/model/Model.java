package model;

import com.mongodb.*;
import org.jongo.Jongo;
import play.Logger;
import play.Play;

public class Model {

    // http://jongo.org/
    static public Jongo createJongo() {
        return new Jongo(_mongoDB);
    }

    static public void InitConnection() {
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
    }

    static public void ShutdownConnection() {
        if (_mongoClient != null) {
            _mongoClient.close();
        }
    }

    static public void ensureDB(DB theMongoDB) {
        DBCollection users = theMongoDB.getCollection("users");

        // Si creando nuevo indice sobre datos que ya existan => .append("dropDups", true)
        users.createIndex(new BasicDBObject("email", 1), new BasicDBObject("unique", true));
        users.createIndex(new BasicDBObject("nickName", 1), new BasicDBObject("unique", true));
    }

    // http://docs.mongodb.org/ecosystem/tutorial/getting-started-with-java-driver/
    static private MongoClient _mongoClient;
    static private DB _mongoDB;
}
