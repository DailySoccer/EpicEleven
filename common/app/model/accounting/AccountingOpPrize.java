package model.accounting;

import org.bson.types.ObjectId;

public class AccountingOpPrize extends AccountingOps {
    public ObjectId contestId;

    public AccountingOpPrize(ObjectId contestId) {
        super();
        this.contestId = contestId;
    }
}
