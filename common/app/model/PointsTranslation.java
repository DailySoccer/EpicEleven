package model;

import org.bson.types.ObjectId;
import org.jongo.marshall.jackson.oid.Id;
import utils.OptaUtils;

import java.util.Date;
/**
 * Created by gnufede on 02/06/14.
 */
public class PointsTranslation {
    @Id
    public ObjectId pointsTranslationId;
    public int eventTypeId;
    public int points;
    public Date timestamp;
    public long unixtimestamp;

    /**
     * Creacion de una entrada de Points Translation
     */
    public static boolean createPointForEvent(int eventType, int points) {
        PointsTranslation pointsTranslation = new PointsTranslation();
        pointsTranslation.eventTypeId = eventType;
        pointsTranslation.points = points;
        pointsTranslation.timestamp = new Date();
        pointsTranslation.unixtimestamp = pointsTranslation.timestamp.getTime();
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
