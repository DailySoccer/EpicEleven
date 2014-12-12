package model.transactions;

import org.bson.types.ObjectId;
import java.math.BigDecimal;

public class AccountOp {
    public ObjectId accountId;
    public BigDecimal value;
    public Integer seqId;

    public AccountOp(ObjectId accountId, BigDecimal value, Integer seqId) {
        this.accountId = accountId;
        this.value = value;
        this.seqId = seqId;
    }
}
