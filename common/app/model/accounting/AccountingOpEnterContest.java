package model.accounting;

import model.Model;
import org.bson.types.ObjectId;

import java.util.List;

public class AccountingOpEnterContest extends AccountingOp {
    public ObjectId contestId;
    public ObjectId contestEntryId;

    public AccountingOpEnterContest() {}

    public AccountingOpEnterContest(ObjectId contestId, ObjectId contestEntryId) {
        super(TransactionType.ENTER_CONTEST);
        this.contestId = contestId;
        this.contestEntryId = contestEntryId;
    }

    static public AccountingOpEnterContest findOne (ObjectId contestId, ObjectId contestEntryId) {
        return Model.accountingTransactions()
                .findOne("{type: #, contestId: #, contestEntryId: #}", TransactionType.ENTER_CONTEST, contestId, contestEntryId)
                .as(AccountingOpEnterContest.class);
    }

    static public AccountingOp create (ObjectId contestId, ObjectId contestEntryId, List<AccountOp> accounts) {
        AccountingOpEnterContest accountingOp = findOne(contestId, contestEntryId);
        if (accountingOp == null) {
            accountingOp = new AccountingOpEnterContest(contestId, contestEntryId);
            accountingOp.accountOps = accounts;
            accountingOp.insert();
        }
        return accountingOp;
    }
}
