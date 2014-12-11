package model.transactions;

import org.bson.types.ObjectId;
import java.math.BigDecimal;

public class AccountOp {
    public ObjectId accountId;
    public BigDecimal value;

    public AccountOp(ObjectId accountId, BigDecimal value) {
        this.accountId = accountId;
        this.value = value;
    }
}
