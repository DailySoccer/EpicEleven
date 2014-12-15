package model.transactions;

import org.bson.types.ObjectId;

public class PrizeChange extends TransactionChange {
    public ObjectId contestId;

    public PrizeChange(ObjectId contestId) {
        super();
        this.contestId = contestId;
    }
}
