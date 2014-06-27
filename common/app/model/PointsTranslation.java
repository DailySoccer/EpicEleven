package model;

import org.bson.types.ObjectId;
import utils.OptaUtils;

import java.util.Date;
/**
 * Created by gnufede on 02/06/14.
 */
public class PointsTranslation {
    public ObjectId pointsTranslationId;
    public int eventTypeId;
    public int points;
    public Date timestamp;
    public long unixtimestamp;
}
