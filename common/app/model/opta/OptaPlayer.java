package model.opta;

import org.bson.types.ObjectId;
import org.jongo.marshall.jackson.oid.Id;

/**
 * Created by gnufede on 02/06/14.
 */
public class OptaPlayer {
    public String optaPlayerId;
    public String name;
    public String firstname;
    public String lastname;
    public String nickname;
    public String position;
    public String teamId;
    public String teamName;
    public long updatedTime;
    public boolean dirty = true;
}
