package model;

import com.mongodb.*;
import org.jongo.MongoCollection;
import org.bson.types.ObjectId;
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
        Logger.info("snapshot: update: start: {} - end: {}", updatedDate, nextDate);

        // update(nextDate, "pointsTranslations", Model.pointsTranslation(), PointsTranslation.class);
        update(nextDate, "templateContests", TemplateContest.class);
        update(nextDate, "templateMatchEvents", TemplateMatchEvent.class);
        update(nextDate, "templateSoccerTeams", TemplateSoccerTeam.class);
        update(nextDate, "templateSoccerPlayers", TemplateSoccerPlayer.class);
        //update(nextDate, "contestEntries", ContestEntry.class);
        //update(nextDate, "contest", Contest.class);

        updatedDate = nextDate;
    }

    private <T> void update(Date nextDate, String collectionName, Class<T> classType) {
        String snapshotName = String.format("snapshot-%s", collectionName);

        MongoCollection collectionSource = Model.jongo().getCollection(snapshotName);
        MongoCollection collectionTarget = Model.jongo().getCollection(collectionName);

        Iterable<T> results = collectionSource.find("{createdAt: {$gte: #, $lte: #}}", updatedDate, nextDate).as(classType);
        List<T> list = utils.ListUtils.asList(results);
         if (!list.isEmpty()) {
            for (T elem : list) {
                collectionTarget.insert(elem);
            }
            Logger.info("snapshot: update {}: {} documents", collectionName, list.size());
        }
    }

    /*
    public void update(Date nextDate) {
        Logger.info("snapshot: update: start: {} - end: {}", updatedDate, nextDate);

        // updateTranslations(nextDate);
        updateTemplateContests(nextDate);
        updateTemplateMatchEvents(nextDate);

        update(nextDate, "pointsTranslations", Model.pointsTranslation(), DataSnapshot<PointsTranslation>.class);
        update(nextDate, "templateContests", Model.templateContests(), TemplateContest.class);
        update(nextDate, "templateMatchEvents", Model.templateMatchEvents(), TemplateMatchEvent.class);
        update(nextDate, "templateSoccerTeams", Model.templateSoccerTeams(), TemplateSoccerTeam.class);
        update(nextDate, "templateSoccerPlayers", Model.templateSoccerPlayers(), TemplateSoccerPlayer.class);
        update(nextDate, "contestEntries", Model.contestEntries(), ContestEntry.class);
        update(nextDate, "contest", Model.contests(), Contest.class);


        updatedDate = nextDate;
    }

    void updateTranslations(Date nextDate) {
        class Data {
            public PointsTranslation pointsTranslations;
        }
        Iterable<Data> results = collection()
                .aggregate("{$unwind: '$pointsTranslations'}")
                .and("{$project: {pointsTranslations: 1}}")
                .and("{$match: {'pointsTranslations.createdAt': {$gte: #, $lt: #}}}", updatedDate, nextDate)
                .as(Data.class);
        List<PointsTranslation> list = new ArrayList<>();
        for (Data data : results) {
            list.add(data.pointsTranslations);
        }
        if (!list.isEmpty()) {
            Model.pointsTranslation().insert(list);
            Logger.info("snapshot: points: {}", list);
        }
    }

    void updateTemplateContests(Date nextDate) {
        class Data {
            public TemplateContest templateContests;
        }
        Iterable<Data> results = collection()
                .aggregate("{$unwind: '$templateContests'}")
                .and("{$project: {templateContests: 1}}")
                .and("{$match: {'templateContests.createdAt': {$gte: #, $lt: #}}}", updatedDate, nextDate)
                .as(Data.class);
        List<TemplateContest> list = new ArrayList<>();
        for (Data data : results) {
            list.add(data.templateContests);
        }
        if (!list.isEmpty()) {
            Model.templateContests().insert(list);
            Logger.info("snapshot: templateContests: {}", list);
        }
    }

    class DataTemplateMatchEvent {
        public TemplateMatchEvent templateMatchEvents;
    }
    void updateTemplateMatchEvents(Date nextDate) {
        Iterable<DataTemplateMatchEvent> results = collection()
                .aggregate("{$unwind: '$templateMatchEvents'}")
                .and("{$project: {templateMatchEvents: 1}}")
                //.and("{$match: {'templateMatchEvents.createdAt': {$gte: #, $lt: #}}}", updatedDate, nextDate)
                .as(DataTemplateMatchEvent.class);
        List<TemplateMatchEvent> list = new ArrayList<>();
        for (DataTemplateMatchEvent data : results) {
            list.add(data.templateMatchEvents);
        }
        if (!list.isEmpty()) {
            Model.templateMatchEvents().insert(list);
            Logger.info("snapshot: templateMatchEvents: {}", list);
        }
    }

    static public Snapshot create() {
        if (!Model.mongoDB().collectionExists(snapshotDBName)) {
            Model.mongoDB().createCollection(snapshotDBName, new BasicDBObject());
        }

        // TODO: Lo borramos todo. Únicamente almacenamos uno
        collection().remove();

        Snapshot snapshot   = new Snapshot();

        snapshot.pointsTranslations = asList(Model.pointsTranslation(), PointsTranslation.class);
        snapshot.templateContests = asList(Model.templateContests(), TemplateContest.class);
        snapshot.templateMatchEvents = asList(Model.templateMatchEvents(), TemplateMatchEvent.class);
        snapshot.templateSoccerTeams = asList(Model.templateSoccerTeams(), TemplateSoccerTeam.class);
        snapshot.templateSoccerPlayers = asList(Model.templateSoccerPlayers(), TemplateSoccerPlayer.class);
        snapshot.contestEntries = asList(Model.contestEntries(), ContestEntry.class);
        snapshot.contest = asList(Model.contests(), Contest.class);

        snapshot.createdAt  = GlobalDate.getCurrentDate();

        collection().insert(snapshot);

        return snapshot;
    }
    */

    static public Snapshot create() {
        if (!Model.mongoDB().collectionExists(snapshotDBName)) {
            Model.mongoDB().createCollection(snapshotDBName, new BasicDBObject());
        }

        Snapshot snapshot   = new Snapshot();

        create("pointsTranslations", PointsTranslation.class);
        create("templateContests", TemplateContest.class);
        create("templateMatchEvents", TemplateMatchEvent.class);
        create("templateSoccerTeams", TemplateSoccerTeam.class);
        create("templateSoccerPlayers", TemplateSoccerPlayer.class);
        //create("contestEntries", ContestEntry.class);
        //create("contests", Contest.class);

        collection().insert(snapshot);

        return snapshot;
    }

    static private <T> void create(String collectionName, Class<T> classType) {
        String snapshotName = String.format("snapshot-%s", collectionName);

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

    static public Snapshot getLast() {
        return collection().findOne().as(Snapshot.class);
    }

    static private <T> ArrayList<T> asList(MongoCollection collection, Class<T> classType) {
        Iterable<T> results = collection.find().as(classType);
        return new ArrayList<T>(utils.ListUtils.asList(results));
    }

    static final private String snapshotDBName = "snapshot";
}
