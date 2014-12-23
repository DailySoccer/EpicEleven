package model.accounting;


import com.mongodb.WriteResult;
import model.Model;
import org.bson.types.ObjectId;

import java.util.List;

public class AccountingOpCancelContestEntry extends AccountingOp {
    public ObjectId contestId;
    public ObjectId contestEntryId;

    public AccountingOpCancelContestEntry() {}

    public AccountingOpCancelContestEntry(ObjectId contestId, ObjectId contestEntryId) {
        super(TransactionType.CANCEL_CONTEST_ENTRY);
        this.contestId = contestId;
        this.contestEntryId = contestEntryId;
    }

    static public AccountingOpCancelContestEntry findOne (ObjectId contestId, ObjectId contestEntryId) {
        return Model.accountingTransactions()
                .findOne("{type: #, contestId: #, contestEntryId: #}", TransactionType.CANCEL_CONTEST_ENTRY, contestId, contestEntryId)
                .as(AccountingOpCancelContestEntry.class);
    }

    static public AccountingOp create (ObjectId contestId, ObjectId contestEntryId, List<AccountOp> accounts) {
        AccountingOpCancelContestEntry accountingOp = findOne(contestId, contestEntryId);
        if (accountingOp == null) {
            accountingOp = new AccountingOpCancelContestEntry(contestId, contestEntryId);
            accountingOp.accounts = accounts;
            WriteResult result = Model.accountingTransactions().insert(accountingOp);
            if (result.getN() > 0) {
                play.Logger.info(accountingOp.toJson());
            }
        }
        return accountingOp;
    }
}
