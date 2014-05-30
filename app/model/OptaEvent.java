package model;

import java.util.Date;

/**
 * Created by gnufede on 28/05/14.
 */
public class OptaEvent {
    public int type_id;
    public int event_id;
    public int player_id;
    public int[] qualifiers;
    public Date timestamp;
    public long unixtimestamp;
    public Date last_modified;
}
