package model;

import org.bson.types.ObjectId;
import org.jongo.marshall.jackson.oid.Id;

import java.util.List;

public class TemplateContest {

    @Id
    public ObjectId templateContestId;

    public String name;             // Auto-gen if blank
    public String postName;         // This goes in parenthesis

    public int minInstances;        // Minimum desired number of instances that we want running at any given moment
    public int maxEntries;

    public int salaryCap;
    public int entryFee;
    public PrizeType prizeType;

    public List<ObjectId> templatebMatchEventIds;  // We rather have it here that normalize it in a N:N table

    public TemplateContest() {}
}
