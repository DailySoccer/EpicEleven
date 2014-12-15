package model.transactions;

import model.Model;
import org.bson.types.ObjectId;
import java.math.BigDecimal;
import java.util.List;

public class AccountOp {
    public ObjectId accountId;
    public BigDecimal value;
    public BigDecimal cachedBalance;
    public Integer seqId;

    public AccountOp() {}

    public AccountOp(ObjectId accountId, BigDecimal value, Integer seqId) {
        this.accountId = accountId;
        this.value = value;
        this.seqId = seqId;
    }

    public boolean canCommit() {
        return Model.transactions()
                .count("{ \"changes.accounts\": {$elemMatch: { accountId: #, seqId: { $lt: # } }}, proc: #}",
                        accountId, seqId, Transaction.TransactionProc.UNCOMMITTED) == 0;
    }

    public void updateBalance() {
        BigDecimal lastBalance = getLastBalance();
        Model.transactions().update("{ \"changes.accounts\": { $elemMatch: {accountId: #, seqId: #} } }", accountId, seqId).with("{$set: {\"changes.accounts.$.cachedBalance\": #}}", lastBalance.add(value).doubleValue());
    }

    public BigDecimal getLastBalance() {
        if (seqId == 1) {
            return new BigDecimal(0);
        }

        List<AccountOp> accountOp = Model.transactions()
                .aggregate("{$match: { \"changes.accounts.accountId\": #, proc: #, state: \"VALID\"}}", accountId, Transaction.TransactionProc.COMMITTED)
                .and("{$unwind: \"$changes.accounts\"}")
                .and("{$match: {\"changes.accounts.accountId\": #}}", accountId)
                .and("{$project: { \"changes.accounts.seqId\": 1, \"changes.accounts.cachedBalance\": 1 }}")
                .and("{$sort: { \"changes.accounts.seqId\": -1 }}")
                .and("{$limit: 1}")
                .and("{$group: {_id: \"balance\", cachedBalance: { $first: \"$changes.accounts.cachedBalance\" }}}")
                .as(AccountOp.class);

        return (!accountOp.isEmpty()) ? accountOp.get(0).cachedBalance : new BigDecimal(0);
    }
}
