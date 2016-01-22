package model;

import com.mongodb.*;
import org.jongo.Jongo;
import org.jongo.MongoCollection;
import play.Logger;
import play.Play;

import java.util.Date;

public class Snapshot {

    public Date createdAt;

    static public Snapshot instance() {
        if (_instance == null) {
            init();
            _instance = collection().findOne().as(Snapshot.class);
            if (_instance == null) {
                _instance = new Snapshot();
            }
        }
        return _instance;
    }

    public Snapshot save() {
        if (collection() == null) {
            throw new RuntimeException("No hay coleccion donde guardar");
        }

        createdAt = GlobalDate.getCurrentDate();

        createInDB();
        collection().remove();
        collection().insert(_instance);
        return _instance;
    }

    public void load() {
        DBObject copyOp = new BasicDBObject("copydb", "1").
                                     append("fromdb", snapshotDBName).
                                     append("todb",   "dailySoccerDB");

        Model.reset(true);

        CommandResult a = _mongoDBAdmin.command(copyOp);
        if (a.getErrorMessage() != null) {
            Logger.error(a.getErrorMessage());
        }
    }

    public String getName() {
        return createdAt!=null? GlobalDate.formatDate(createdAt): "none";
    }

    static private void createInDB() {
        DBObject copyOp = new BasicDBObject("copydb", "1").
                                     append("fromdb", "dailySoccerDB").
                                     append("todb", snapshotDBName);

        _mongoDBSnapshot.dropDatabase();

        CommandResult a = _mongoDBAdmin.command(copyOp);
        if (a.getErrorMessage() != null) {
            Logger.error(a.getErrorMessage());
        }
    }

    static private void init() {
        MongoClientURI mongoClientURI = new MongoClientURI(Play.application().configuration().getString("mongodb.uri"));

        try {
            MongoClient mongoClient = new MongoClient(mongoClientURI);

            _mongoDBAdmin = mongoClient.getDB("admin");
            _mongoDBSnapshot = mongoClient.getDB(snapshotCollectionName);

            _jongoSnapshot = new Jongo(_mongoDBSnapshot);
        }
        catch (Exception exc) {
            Logger.error("Snapshot: Error initializating MongoDB {}/{}: {}", mongoClientURI.getHosts(),
                         mongoClientURI.getDatabase(), exc.toString());
        }
    }


    static private MongoCollection collection() {
        return (_jongoSnapshot != null) ? _jongoSnapshot.getCollection(snapshotCollectionName) : null;
    }

    static private DB _mongoDBAdmin;
    static private DB _mongoDBSnapshot;
    static private Jongo _jongoSnapshot;

    static final private String snapshotCollectionName = "snapshot";
    static final private String snapshotDBName = "snapshot";
    static private Snapshot _instance;

}
