package model;

import com.mongodb.*;
import org.jongo.MongoCollection;
import com.fasterxml.jackson.annotation.JsonIgnore;

import play.Logger;
import java.util.Date;
import java.util.List;
import java.util.ArrayList;

public class Snapshot {
    static public MongoCollection collection() { return Model.jongo().getCollection(snapshotDBName); }

    public ArrayList<PointsTranslation> pointsTranslations;
    public ArrayList<TemplateContest> templateContests;
    public ArrayList<TemplateMatchEvent> templateMatchEvents;
    public ArrayList<TemplateSoccerTeam> templateSoccerTeams;
    public ArrayList<TemplateSoccerPlayer> templateSoccerPlayers;
    public ArrayList<ContestEntry> contestEntries;
    public ArrayList<Contest> contest;

    public Date createdAt;

    @JsonIgnore
    private Date updatedDate;

    // Constructor por defecto (necesario para Jongo: "unmarshall result to class")
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
            //update(nextDate, "contestEntries", ContestEntry.class);
            //update(nextDate, "contest", Contest.class);

            updatedDate = nextDate;
        }
    }

    //
    // Se necesita que las clases puedan proporcionar el "ObjectId" de Jongo
    //  dado que cada clase sobreescribe el nombre por defecto "_id", no es posible obtenerlo de forma genérica
    //
    private <T extends JongoId & Initializer> void update(Date nextDate, String collectionName, Class<T> classType) {
        String snapshotName = String.format("%s-%s", snapshotDBName, collectionName);

        MongoCollection collectionSource = Model.jongo().getCollection(snapshotName);
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

    static public Snapshot create() {
        if (!Model.mongoDB().collectionExists(snapshotDBName)) {
            Model.mongoDB().createCollection(snapshotDBName, new BasicDBObject());
        }

        collection().remove();

        Snapshot snapshot   = new Snapshot();

        create("pointsTranslation", PointsTranslation.class);
        create("templateContests", TemplateContest.class);
        create("templateMatchEvents", TemplateMatchEvent.class);
        create("templateSoccerTeams", TemplateSoccerTeam.class);
        create("templateSoccerPlayers", TemplateSoccerPlayer.class);
        //create("contestEntries", ContestEntry.class);
        //create("contests", Contest.class);

        snapshot.createdAt = GlobalDate.getCurrentDate();

        collection().insert(snapshot);

        return snapshot;
    }

    static private <T> void create(String collectionName, Class<T> classType) {
        String snapshotName = String.format("%s-%s", snapshotDBName, collectionName);

        MongoCollection collectionSource = Model.jongo().getCollection(collectionName);
        MongoCollection collectionTarget = Model.jongo().getCollection(snapshotName);

        // Vaciar la collection (unicamente mantenemos 1 snapshot)
        collectionTarget.remove();

        Iterable<T> results = collectionSource.find().as(classType);
        List<T> list = utils.ListUtils.asList(results);
        if (!list.isEmpty()) {
            for (T elem : list) {
                collectionTarget.insert(elem);
            }
        }
    }

    static public String getName() {
        Snapshot snapshot = getLast();
        return (snapshot != null) ? snapshot.createdAt.toString() : "none";
    }

    static public Snapshot getLast() {
        return collection().findOne().as(Snapshot.class);
    }

    static private <T> ArrayList<T> asList(MongoCollection collection, Class<T> classType) {
        Iterable<T> results = collection.find().as(classType);
        return new ArrayList<T>(utils.ListUtils.asList(results));
    }

    static final private String snapshotDBName = "snapshot";
}
