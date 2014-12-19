package model.accounting;

import com.mongodb.WriteResult;
import model.Model;
import org.bson.types.ObjectId;

public class AccountingOpsEnterContest extends AccountingOps {
    public ObjectId contestId;
    public ObjectId contestEntryId;

    public AccountingOpsEnterContest(ObjectId contestId, ObjectId contestEntryId) {
        super();
        this.contestId = contestId;
        this.contestEntryId = contestEntryId;
    }

    static public AccountingOp create (AccountingOpsEnterContest changes) {
        AccountingOp accountingOp = new AccountingOp(AccountingOp.TransactionType.ENTER_CONTEST);
        accountingOp.changes = changes;
        WriteResult result = Model.accountingTransactions()
                .update("{type: #, 'changes.contestId': #, 'changes.contestEntryId': #}",
                        accountingOp.type, changes.contestId, changes.contestEntryId)
                .upsert()
                .with(accountingOp);
        if (result.getN() > 0) {
            play.Logger.info(accountingOp.toJson());
        }
        return accountingOp;
    }
}
