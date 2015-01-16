package model.accounting;

import model.Model;
import org.bson.types.ObjectId;

import java.util.List;

public class AccountingTranEnterContest extends AccountingTran {
    public ObjectId contestId;
    public ObjectId contestEntryId;

    public AccountingTranEnterContest() {}

    public AccountingTranEnterContest(ObjectId contestId, ObjectId contestEntryId) {
        super(TransactionType.ENTER_CONTEST);
        this.contestId = contestId;
        this.contestEntryId = contestEntryId;
    }

    static public AccountingTranEnterContest findOne (ObjectId contestId, ObjectId contestEntryId) {
        return Model.accountingTransactions()
                .findOne("{type: #, contestId: #, contestEntryId: #}", TransactionType.ENTER_CONTEST, contestId, contestEntryId)
                .as(AccountingTranEnterContest.class);
    }

    static public AccountingTran create (ObjectId contestId, ObjectId contestEntryId, List<AccountOp> accounts) {
        AccountingTranEnterContest accountingOp = findOne(contestId, contestEntryId);
        if (accountingOp == null) {
            accountingOp = new AccountingTranEnterContest(contestId, contestEntryId);
            accountingOp.accountOps = accounts;
            accountingOp.insertAndCommit();
        }
        return accountingOp;
    }
}
