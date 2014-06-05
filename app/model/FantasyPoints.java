package model;

import org.bson.types.ObjectId;

import java.util.Date;

/**
 * Created by gnufede on 04/06/14.
 */
public class FantasyPoints {
    public int points;
    public int event_type;
    public int player_id;
    public ObjectId event_id;
    public ObjectId pointsTranslation_id;
    public Date timestamp;
    public long unixtimestamp;
}
