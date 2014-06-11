package model.opta;

import org.bson.types.ObjectId;

import java.util.Date;
/**
 * Created by gnufede on 02/06/14.
 */
public class PointsTranslation { //TODO: Quitar del namespace
    public ObjectId _id;
    public int eventCode; //eventTypeId
    public int points;
    public Date timestamp;
    public long unixtimestamp;
}
