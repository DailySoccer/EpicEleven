package model.accounting;

import model.Model;
import org.bson.types.ObjectId;

import java.util.List;

public class AccountingTranCancelContest extends AccountingTran {
    public ObjectId contestId;

    public AccountingTranCancelContest() {}

    public AccountingTranCancelContest(ObjectId contestId) {
        super(TransactionType.CANCEL_CONTEST);
        this.contestId = contestId;
    }

    static public AccountingTranCancelContest findOne (ObjectId contestId) {
        return Model.accountingTransactions()
                .findOne("{type: #, contestId: #}", TransactionType.CANCEL_CONTEST, contestId)
                .as(AccountingTranCancelContest.class);
    }

    static public AccountingTran create (ObjectId contestId, List<AccountOp> accounts) {
        AccountingTranCancelContest accountingOp = findOne(contestId);
        if (accountingOp == null) {
            accountingOp = new AccountingTranCancelContest(contestId);
            accountingOp.accountOps = accounts;
            accountingOp.insert();
        }
        return accountingOp;
    }
}
