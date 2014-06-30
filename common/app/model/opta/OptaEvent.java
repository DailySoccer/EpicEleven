package model.opta;

import model.Model;
import org.bson.types.ObjectId;
import org.jongo.marshall.jackson.oid.Id;
import utils.ListUtils;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;


/**
 * Created by gnufede on 28/05/14.
 */
public class OptaEvent {

    @Id
    public ObjectId optaEventId;
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

    static public List<OptaEvent> filter(String optaMatchId, String optaPlayerId) {
        String playerId = Model.getPlayerIdFromOpta(optaPlayerId);
        String matchId = Model.getMatchEventIdFromOpta(optaMatchId);

        Iterable<OptaEvent> optaEventResults = Model.optaEvents().find("{optaPlayerId: #, gameId: #}",
                playerId, matchId).as(OptaEvent.class);
        return ListUtils.asList(optaEventResults);
    }
}
