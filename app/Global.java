import com.mongodb.*;
import model.Model;
import play.*;

// http://www.playframework.com/documentation/2.2.x/JavaGlobal
public class Global extends GlobalSettings {

    public void onStart(Application app) {
        Logger.info("Application has started");

        String mongodbUri = Play.application().configuration().getString("mongodb.uri");
        Logger.info("The MongoDB uri is {}", mongodbUri);

        try {
            Model.mongoClient = new MongoClient(new MongoClientURI(mongodbUri));

            // Let's make sure our DB has the neccesary collections and indexes
            ensureDB(Model.mongoClient);

        } catch (Exception exc) {
            Logger.error("Error initializating MongoDB {}\n{}", mongodbUri, exc.toString());
        }
    }

    static public void ensureDB(MongoClient theMongoClient) {
        DB db = theMongoClient.getDB(Model.dbName);
        DBCollection users = db.getCollection("users");

        // Si creando nuevo indice sobre datos que ya existan => .append("dropDups", true)
        users.createIndex(new BasicDBObject("email", 1), new BasicDBObject("unique", true));
        users.createIndex(new BasicDBObject("nickName", 1), new BasicDBObject("unique", true));
    }

    public void onStop(Application app) {
        Logger.info("Application shutdown...");

        if (Model.mongoClient != null) {
            Model.mongoClient.close();
        }
    }
}