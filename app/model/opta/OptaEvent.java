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
    public int game_id;
    public int home_team_id;
    public int away_team_id;
    public int competition_id;
    public int season_id;
    public int type_id;
    public int event_id;
    public int player_id;
    public int outcome;
    public ArrayList<Integer> qualifiers;
    public Date timestamp;
    public long unixtimestamp;
    public Date last_modified;

    public OptaEvent(){}

    public boolean hasChanged(OptaEvent other){
        if (other == this) {
            return false;
        }
        if (other == null) {
            return false;
        }
        if (other.last_modified != null && this.last_modified != null){
            System.out.println("this: "+this.last_modified);
            System.out.println("other: "+other.last_modified);
            System.out.println("is before? "+this.last_modified.before(other.last_modified));
            return this.last_modified.before(other.last_modified);
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
                return (this.game_id == other.game_id &&
                        this.home_team_id == other.home_team_id &&
                        this.away_team_id == other.away_team_id &&
                        this.competition_id == other.competition_id &&
                        this.season_id == other.season_id &&
                        this.type_id == other.type_id &&
                        this.event_id == other.event_id &&
                        this.player_id == other.player_id &&
                        this.outcome == other.outcome &&
                        this.qualifiers.equals(other.qualifiers) &&
                        this.timestamp.equals(other.timestamp) &&
                        this.last_modified.equals(other.last_modified) &&
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
