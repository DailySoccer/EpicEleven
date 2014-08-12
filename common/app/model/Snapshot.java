package model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.mongodb.*;
import org.jongo.Jongo;
import org.jongo.MongoCollection;
import play.Logger;
import play.Play;

import java.util.Date;
import java.util.List;

public class Snapshot {

    public Date createdAt;

    static public Snapshot instance() {
        if (_instance == null) {
            _instance = new Snapshot();
            _instance.init();
            Snapshot last = _instance.collection().findOne().as(Snapshot.class);
            if (last != null) {
                _instance = last;
            }
        }
        return _instance;
    }

    public Snapshot() {
        if (collection() == null) {
            return null;
        }
        return
        updatedDate = new Date(0);
    }

    private void init() {
        String mongodbUri = Play.application().configuration().getString("mongodb.uri");
        MongoClientURI mongoClientURI = new MongoClientURI(mongodbUri);

        Logger.info("The MongoDB is {}/{}", mongoClientURI.getHosts(), mongoClientURI.getDatabase());

        try {
            _mongoClient = new MongoClient(mongoClientURI);
            _mongoDBAdmin = _mongoClient.getDB("admin");

            if (!Play.isProd()) {
                _mongoDBSnapshot = _mongoClient.getDB("snapshot");
                _jongoSnapshot = new Jongo(mongoDBSnapshot());
            } else {
                _mongoDBSnapshot = null;
                _jongoSnapshot = null;
            }

        } catch (Exception exc) {
            Logger.error("Error initializating MongoDB {}/{}: {}", mongoClientURI.getHosts(),
                    mongoClientURI.getDatabase(), exc.toString());
        }
    }

    public void dropSnapshotDB() {
        _mongoDBSnapshot.dropDatabase();
    }

    public void update(Date nextDate) {
        if (nextDate.after(updatedDate)) {
            // Logger.info("snapshot: update: start: {} - end: {}", updatedDate, nextDate);

            update(nextDate, "pointsTranslation", PointsTranslation.class);
            update(nextDate, "templateContests", TemplateContest.class);
            update(nextDate, "templateMatchEvents", TemplateMatchEvent.class);
            update(nextDate, "templateSoccerTeams", TemplateSoccerTeam.class);
            update(nextDate, "templateSoccerPlayers", TemplateSoccerPlayer.class);

            updatedDate = nextDate;
        }
    }

    //
    // Se necesita que las clases puedan proporcionar el "ObjectId" de Jongo
    //  dado que cada clase sobreescribe el nombre por defecto "_id", no es posible obtenerlo de forma genérica
    //
    private <T extends JongoId & Initializer> void update(Date nextDate, String collectionName, Class<T> classType) {
        //String snapshotName = String.format("%s-%s", snapshotDBName, collectionName);

        //MongoCollection collectionSource = Model.jongo().getCollection(snapshotName);
        MongoCollection collectionSource = jongoSnapshot().getCollection(collectionName);
        MongoCollection collectionTarget = Model.jongo().getCollection(collectionName);

        Iterable<T> results = collectionSource.find("{createdAt: {$gte: #, $lte: #}}", updatedDate, nextDate).as(classType);
        List<T> list = utils.ListUtils.asList(results);
         if (!list.isEmpty()) {
            for (T elem : list) {
                elem.Initialize();
                collectionTarget.update("{_id: #}", elem.getId()).upsert().with(elem);
            }
            Logger.info("snapshot: update {}: {} documents", collectionName, list.size());
        }
    }


    public Snapshot create() {
        if (collection() == null) {
            return null;
        }

        createdAt = GlobalDate.getCurrentDate();

        createInDB();
        collection().remove();
        collection().insert(_instance);
        return _instance;
    }

    public void load() {
        DBObject copyOp = new BasicDBObject("copydb", "1").
                                     append("fromdb" , "snapshot").
                                     append("todb", "dailySoccerDB");

        Model.resetDB();
        CommandResult a = mongoDBAdmin().command(copyOp);
        if (a.getErrorMessage() != null) {
            Logger.error(a.getErrorMessage());
        }
    }

    public String getName() {
        Snapshot snapshot = getLast();
        return (snapshot != null) ? GlobalDate.formatDate(snapshot.createdAt): "none";
    }

    public Snapshot getLast() {
        if (collection() == null) {
            return null;
        }
        return collection().findOne().as(Snapshot.class);
    }

    private void createInDB() {
        DBObject copyOp = new BasicDBObject("copydb", "1").
                append("fromdb" , "dailySoccerDB").
                append("todb", "snapshot");

        dropSnapshotDB();
        CommandResult a = mongoDBAdmin().command(copyOp);
        if (a.getErrorMessage() != null) {
            Logger.error(a.getErrorMessage());
        }
    }

    private DB mongoDBAdmin() { return _mongoDBAdmin; }
    private DB mongoDBSnapshot() { return _mongoDBSnapshot; }

    private Jongo jongoSnapshot() { return _jongoSnapshot; }

    private MongoCollection collection() {
        return (jongoSnapshot() != null) ? jongoSnapshot().getCollection(snapshotDBName) : null;
    }



    @JsonIgnore
    private MongoClient _mongoClient;
    @JsonIgnore
    final private String snapshotDBName = "snapshot";

    @JsonIgnore
    private DB _mongoDBAdmin;
    @JsonIgnore
    private DB _mongoDBSnapshot;
    @JsonIgnore
    private Jongo _jongoSnapshot;

    @JsonIgnore
    private Date updatedDate;

    @JsonIgnore
    static private Snapshot _instance;
}
