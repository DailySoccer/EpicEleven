package model.transactions;

import org.bson.types.ObjectId;
import java.util.ArrayList;
import java.util.List;

public class PrizeChange extends TransactionChange {
    public ObjectId contestId;

    public PrizeChange(ObjectId contestId) {
        super();
        this.contestId = contestId;
    }
}
