package model.accounting;

import model.Model;
import org.bson.types.ObjectId;

import java.util.List;

public class AccountingTranCancelContest extends AccountingTran {
    public ObjectId contestId;

    public AccountingTranCancelContest() {}

    public AccountingTranCancelContest(String currencyCode, ObjectId contestId) {
        super(currencyCode, TransactionType.CANCEL_CONTEST);
        this.contestId = contestId;
    }

    static public AccountingTranCancelContest findOne (ObjectId contestId) {
        return Model.accountingTransactions()
                .findOne("{type: #, contestId: #}", TransactionType.CANCEL_CONTEST, contestId)
                .as(AccountingTranCancelContest.class);
    }

    static public AccountingTran create (String currencyCode, ObjectId contestId, List<AccountOp> accounts) {
        AccountingTranCancelContest accountingOp = findOne(contestId);
        if (accountingOp == null) {
            accountingOp = new AccountingTranCancelContest(currencyCode, contestId);
            accountingOp.accountOps = accounts;
            accountingOp.insertAndCommit();
        }
        return accountingOp;
    }
}
