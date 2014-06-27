package model.opta;

import org.bson.types.ObjectId;
import org.jongo.marshall.jackson.oid.Id;

/**
 * Created by gnufede on 02/06/14.
 */
public class OptaPlayer {
    @Id
    public ObjectId optaPlayerId;
    public String id;
    public String name;
    public String firstname;
    public String lastname;
    public String position;
    public String teamId;
    public String teamName;
    public long updatedTime;
    public boolean dirty = true;
}
