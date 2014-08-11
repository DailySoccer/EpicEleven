package model;

import org.bson.types.ObjectId;
import org.jongo.marshall.jackson.oid.Id;
import java.util.Date;

public class PointsTranslation implements JongoId, Initializer {
    @Id
    public ObjectId pointsTranslationId;
    public int eventTypeId;
    public int points;
    public Date timestamp;
    public long unixtimestamp;

    public Date createdAt;

    public PointsTranslation() {
    }

    public void Initialize() {
    }

    public ObjectId getId() {
        return pointsTranslationId;
    }

    public static PointsTranslation findOne(ObjectId pointsTranslationId) {
        return Model.pointsTranslation().findOne("{_id: #}", pointsTranslationId).as(PointsTranslation.class);
    }

    /**
     * Creacion de una entrada de Points Translation
     */
    public static boolean createPointForEvent(int eventType, int points) {
        PointsTranslation pointsTranslation = new PointsTranslation();
        pointsTranslation.eventTypeId = eventType;
        pointsTranslation.points = points;
        pointsTranslation.timestamp = new Date();
        pointsTranslation.unixtimestamp = pointsTranslation.timestamp.getTime();
        pointsTranslation.createdAt = GlobalDate.getCurrentDate();
        Model.pointsTranslation().insert(pointsTranslation);
        return true;
    }

    /**
     * Edicion de una entrada de Points Translation
     */
    public static boolean editPointForEvent(ObjectId pointsTranslationId, int points) {
        Model.pointsTranslation().update(pointsTranslationId).with("{$set: {points: #}}", points);
        return true;
    }
}
