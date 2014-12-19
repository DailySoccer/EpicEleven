package model.accounting;

import com.mongodb.WriteResult;
import model.Model;
import org.bson.types.ObjectId;

import java.util.List;

public class AccountingOpEnterContest extends AccountingOp {
    public ObjectId contestId;
    public ObjectId contestEntryId;

    public AccountingOpEnterContest(ObjectId contestId, ObjectId contestEntryId) {
        super(TransactionType.ENTER_CONTEST);
        this.contestId = contestId;
        this.contestEntryId = contestEntryId;
    }

    static public AccountingOp create (ObjectId contestId, ObjectId contestEntryId, List<AccountOp> accounts) {
        AccountingOpEnterContest accountingOp = new AccountingOpEnterContest(contestId, contestEntryId);
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
