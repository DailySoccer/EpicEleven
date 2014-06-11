package model.opta;

import org.jongo.marshall.jackson.oid.Id;
import org.bson.types.ObjectId;

import java.util.ArrayList;
import java.util.Date;



/**
 * Created by gnufede on 28/05/14.
 */
public class OptaEvent {

    public ObjectId _id;
    public String gameId;
    public String homeTeamId;
    public String awayTeamId;
    public String competitionId;
    public String seasonId;
    public int typeId;
    public int eventId;
    public String playerId;
    //ObjectId?
    public int parentId;
    // <DEBUG>
    public int outcome;
    public ArrayList<Integer> qualifiers;
    // </DEBUG>
    public Date timestamp;
    //TODO: Remove
    public long unixtimestamp;
    public Date lastModified;

    public OptaEvent(){}

    public boolean hasChanged(OptaEvent other){
        if (other == this) {
            return false;
        }
        if (other == null) {
            return false;
        }
        //TODO: Last modified no debería ser null
        if (other.lastModified != null && this.lastModified != null){
            return this.lastModified.before(other.lastModified);
        }
        return false;
    }

    //TODO: Borrar
    public boolean equals(Object obj){
        //NOT FULLY FUNCTIONAL, has bugs
        if (obj == this) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (obj instanceof OptaEvent) {
            try {
                OptaEvent other = (OptaEvent) obj;
                if (other == null){
                    System.out.println("other is null");
                    return false;
                }
                return (this.gameId == other.gameId &&
                        this.homeTeamId == other.homeTeamId &&
                        this.awayTeamId == other.awayTeamId &&
                        this.competitionId == other.competitionId &&
                        this.seasonId == other.seasonId &&
                        this.typeId == other.typeId &&
                        this.eventId == other.eventId &&
                        this.playerId == other.playerId &&
                        this.outcome == other.outcome &&
                        this.qualifiers.equals(other.qualifiers) &&
                        this.timestamp.equals(other.timestamp) &&
                        this.lastModified.equals(other.lastModified) &&
                        this.unixtimestamp == other.unixtimestamp);
            } catch (ClassCastException e) {
                return false;
            } catch (NullPointerException a) {
                a.printStackTrace();
            }

        }
        return false;
    }
}
