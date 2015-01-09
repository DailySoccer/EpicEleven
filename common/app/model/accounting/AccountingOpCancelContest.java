package model.accounting;

import com.mongodb.WriteResult;
import model.Model;
import org.bson.types.ObjectId;

import java.util.List;

public class AccountingOpCancelContest extends AccountingOp {
    public ObjectId contestId;

    public AccountingOpCancelContest() {}

    public AccountingOpCancelContest(ObjectId contestId) {
        super(TransactionType.CANCEL_CONTEST);
        this.contestId = contestId;
    }

    static public AccountingOpCancelContest findOne (ObjectId contestId) {
        return Model.accountingTransactions()
                .findOne("{type: #, contestId: #}", TransactionType.CANCEL_CONTEST, contestId)
                .as(AccountingOpCancelContest.class);
    }

    static public AccountingOp create (ObjectId contestId, List<AccountOp> accounts) {
        AccountingOpCancelContest accountingOp = findOne(contestId);
        if (accountingOp == null) {
            accountingOp = new AccountingOpCancelContest(contestId);
            accountingOp.accounts = accounts;
            accountingOp.insert();
        }
        return accountingOp;
    }
}
