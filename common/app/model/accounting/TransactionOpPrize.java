package model.accounting;

import org.bson.types.ObjectId;

public class TransactionOpPrize extends TransactionOps {
    public ObjectId contestId;

    public TransactionOpPrize(ObjectId contestId) {
        super();
        this.contestId = contestId;
    }
}
