package model;

import org.bson.types.ObjectId;
import org.jongo.marshall.jackson.oid.Id;

import java.util.List;

public class Contest {

    @Id
    public ObjectId contestId;

    public String name;

    public int currentEntries;
    public int maxEntries;

    public int salaryCap;
    public int entryFee;
    public PrizeType prizeType;

    public List<ObjectId> matchEventIds;

    public Contest() {}
}
