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
            init();
            _instance = collection().findOne().as(Snapshot.class);
            if (_instance == null) {
                _instance = new Snapshot();
            }
        }
        return _instance;
    }

    public Snapshot() {
        updatedDate = new Date(0);
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
                                     append("fromdb" , "snapshot").
                                     append("todb", "dailySoccerDB");

        Model.resetDB();
        CommandResult a = mongoDBAdmin().command(copyOp);
        if (a.getErrorMessage() != null) {
            Logger.error(a.getErrorMessage());
        }
    }

    public String getName() {
        return createdAt!=null? GlobalDate.formatDate(createdAt): "none";
    }

    static public Snapshot getLast() {
        return instance();
        //Ahora es la instancia
        /*
        if (collection() == null) {
            return null;
        }
        return collection().findOne().as(Snapshot.class);
        */
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

    static private void init() {
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

    private void dropSnapshotDB() {
        _mongoDBSnapshot.dropDatabase();
    }


    static private DB mongoDBAdmin() { return _mongoDBAdmin; }
    static private DB mongoDBSnapshot() { return _mongoDBSnapshot; }

    static private Jongo jongoSnapshot() { return _jongoSnapshot; }

    static private MongoCollection collection() {
        return (jongoSnapshot() != null) ? jongoSnapshot().getCollection(snapshotDBName) : null;
    }



    @JsonIgnore
    static private MongoClient _mongoClient;
    @JsonIgnore
    static final private String snapshotDBName = "snapshot";

    @JsonIgnore
    static private DB _mongoDBAdmin;
    @JsonIgnore
    static private DB _mongoDBSnapshot;
    @JsonIgnore
    static private Jongo _jongoSnapshot;

    @JsonIgnore
    static private Snapshot _instance;

    @JsonIgnore
    private Date updatedDate;

}
