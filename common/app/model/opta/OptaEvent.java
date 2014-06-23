package model.opta;

import org.bson.types.ObjectId;

import java.util.ArrayList;
import java.util.Date;



/**
 * Created by gnufede on 28/05/14.
 */
public class OptaEvent {

    public ObjectId _id; //TODO: optaEventId
    public String gameId;
    public String homeTeamId;
    public String awayTeamId;
    public String competitionId;
    public String seasonId;
    public int typeId;
    public int eventId;
    public String optaPlayerId;
    //ObjectId?
    public int parentId;
    public int periodId;
    // <DEBUG>
    public int outcome;
    public ArrayList<Integer> qualifiers = new ArrayList<>();
    // </DEBUG>
    public Date timestamp;
    //TODO: Remove
    public long unixtimestamp;
    public Date lastModified;

    public int points;
    public ObjectId pointsTranslationId;

    public OptaEvent(){}

    public boolean hasChanged(OptaEvent other){
        if (other == this) {
            return false;
        }
        if (other == null) {
            return false;
        }
        return this.lastModified.before(other.lastModified);
    }

}
