package model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.mongodb.*;
import model.opta.OptaCompetition;
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

            update(nextDate, "optaCompetitions", OptaCompetition.class);
            update(nextDate, "pointsTranslation", PointsTranslation.class);
            update(nextDate, "templateContests", TemplateContest.class);
            update(nextDate, "templateMatchEvents", TemplateMatchEvent.class);
            update(nextDate, "templateSoccerTeams", TemplateSoccerTeam.class);
            update(nextDate, "templateSoccerPlayers", TemplateSoccerPlayer.class);

            updatedDate = nextDate;
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
                                     append("fromdb", "snapshot").
                                     append("todb",   "dailySoccerDB");

        Model.resetDB();
        CommandResult a = _mongoDBAdmin.command(copyOp);
        if (a.getErrorMessage() != null) {
            Logger.error(a.getErrorMessage());
        }
    }

    public String getName() {
        return createdAt!=null? GlobalDate.formatDate(createdAt): "none";
    }

    //
    // Se necesita que las clases puedan proporcionar el "ObjectId" de Jongo
    //  dado que cada clase sobreescribe el nombre por defecto "_id", no es posible obtenerlo de forma genérica
    //
    private <T extends JongoId & Initializer> void update(Date nextDate, String collectionName, Class<T> classType) {
        //String snapshotName = String.format("%s-%s", snapshotDBName, collectionName);

        //MongoCollection collectionSource = Model.jongo().getCollection(snapshotName);
        MongoCollection collectionSource = _jongoSnapshot.getCollection(collectionName);
        MongoCollection collectionTarget = _jongoRegular.getCollection(collectionName);

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


    static private void createInDB() {
        DBObject copyOp = new BasicDBObject("copydb", "1").
                                     append("fromdb", "dailySoccerDB").
                                     append("todb", "snapshot");

        dropSnapshotDB();
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
            _mongoDBSnapshot = mongoClient.getDB("snapshot");

            _jongoSnapshot = new Jongo(_mongoDBSnapshot);
            _jongoRegular = new Jongo(mongoClient.getDB(mongoClientURI.getDatabase()));
        }
        catch (Exception exc) {
            Logger.error("Snapshot: Error initializating MongoDB {}/{}: {}", mongoClientURI.getHosts(),
                         mongoClientURI.getDatabase(), exc.toString());
        }
    }

    static private void dropSnapshotDB() {
        _mongoDBSnapshot.dropDatabase();
    }

    static private MongoCollection collection() {
        return (_jongoSnapshot != null) ? _jongoSnapshot.getCollection(snapshotDBName) : null;
    }

    static private DB _mongoDBAdmin;
    static private DB _mongoDBSnapshot;
    static private Jongo _jongoSnapshot;
    static private Jongo _jongoRegular;

    @JsonIgnore
    private Date updatedDate;

    static final private String snapshotDBName = "snapshot";
    static private Snapshot _instance;

}
