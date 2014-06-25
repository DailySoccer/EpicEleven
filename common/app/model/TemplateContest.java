package model;

import org.bson.types.ObjectId;
import org.jongo.marshall.jackson.oid.Id;

import java.util.Date;
import java.util.*;

public class TemplateContest {
    public enum State {
        OFF(0),
        ACTIVE(1),
        LIVE(2),
        HISTORY(3);

        public final int id;

        State(int id) {
            this.id = id;
        }

        public static Map<String, String> options(){
            LinkedHashMap<String, String> vals = new LinkedHashMap<String, String>();
            for (State cType : State.values()) {
                vals.put(cType.name(), cType.name());
            }
            return vals;
        }
    }

    @Id
    public ObjectId templateContestId;

    public State state = State.OFF;

    public String name;             // Auto-gen if blank
    public String postName;         // This goes in parenthesis

    public int minInstances;        // Minimum desired number of instances that we want running at any given moment
    public int maxEntries;

    public int salaryCap;
    public int entryFee;
    public PrizeType prizeType;

    public Date startDate;

    public List<ObjectId> templateMatchEventIds;  // We rather have it here that normalize it in a N:N table

    public TemplateContest() {}

    public boolean isActive() { return (state == State.ACTIVE); }
}
