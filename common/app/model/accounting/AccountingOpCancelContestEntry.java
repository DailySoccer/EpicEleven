package model.accounting;


import com.mongodb.WriteResult;
import model.Model;
import org.bson.types.ObjectId;

import java.util.List;

public class AccountingOpCancelContestEntry extends AccountingOp {
    public ObjectId contestId;
    public ObjectId contestEntryId;

    public AccountingOpCancelContestEntry() {
    }

    public AccountingOpCancelContestEntry(ObjectId contestId, ObjectId contestEntryId) {
        super(TransactionType.CANCEL_CONTEST_ENTRY);
        this.contestId = contestId;
        this.contestEntryId = contestEntryId;
    }

    static public AccountingOp create (ObjectId contestId, ObjectId contestEntryId, List<AccountOp> accounts) {
        AccountingOpCancelContestEntry accountingOp = new AccountingOpCancelContestEntry(contestId, contestEntryId);
        accountingOp.accounts = accounts;
        WriteResult result = Model.accountingTransactions()
                .update("{type: #, contestId: #, contestEntryId: #}",
                        accountingOp.type, accountingOp.contestId, accountingOp.contestEntryId)
                .upsert()
                .with(accountingOp);
        if (result.getN() > 0) {
            play.Logger.info(accountingOp.toJson());
        }
        return accountingOp;
    }
}
