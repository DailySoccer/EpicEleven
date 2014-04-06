package model;

import com.mongodb.MongoClient;
import org.jongo.Jongo;

public class Model {
    // http://docs.mongodb.org/ecosystem/tutorial/getting-started-with-java-driver/
    static public MongoClient mongoClient;

    // http://jongo.org/
    static public Jongo createJongo() {
        return new Jongo(Model.mongoClient.getDB(Model.dbName));
    }

    // TODO: Should be this included in the conf file?
    static public String dbName = "app23671191";
}
