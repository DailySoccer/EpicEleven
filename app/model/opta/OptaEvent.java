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
    public int gameId;
    public int homeTeamId;
    public int awayTeamId;
    public int competitionId;
    public int seasonId;
    public int typeId;
    public int eventId;
    public int playerId;
    public int parentId;
    public int outcome;
    public ArrayList<Integer> qualifiers;
    public Date timestamp;
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
        if (other.lastModified != null && this.lastModified != null){
            return this.lastModified.before(other.lastModified);
        }
        return false;
    }

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
