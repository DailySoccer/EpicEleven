package model.accounting;

import com.mongodb.WriteResult;
import model.Model;
import org.bson.types.ObjectId;

public class AccountingOpsPrize extends AccountingOps {
    public ObjectId contestId;

    public AccountingOpsPrize(ObjectId contestId) {
        super();
        this.contestId = contestId;
    }

    static public AccountingOp create(AccountingOpsPrize changes) {
        AccountingOp accountingOp = new AccountingOp(AccountingOp.TransactionType.PRIZE);
        accountingOp.changes = changes;
        WriteResult result = Model.accountingTransactions().update("{type: #, 'changes.contestId': #}",
                accountingOp.type, changes.contestId).upsert().with(accountingOp);
        if (result.getN() > 0) {
            play.Logger.info(accountingOp.toJson());
        }
        return accountingOp;
    }
}
