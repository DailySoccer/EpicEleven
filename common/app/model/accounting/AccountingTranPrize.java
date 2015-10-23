package model.accounting;

import model.Model;
import org.bson.types.ObjectId;

import java.util.List;

public class AccountingTranPrize extends AccountingTran {
    public ObjectId contestId;

    public AccountingTranPrize() {}

    public AccountingTranPrize(String currencyCode, ObjectId contestId) {
        super(currencyCode, TransactionType.PRIZE);
        this.contestId = contestId;
    }

    static public AccountingTranPrize findOne (ObjectId contestId) {
        return Model.accountingTransactions()
                .findOne("{type: #, contestId: #}", TransactionType.PRIZE, contestId)
                .as(AccountingTranPrize.class);
    }

    static public AccountingTran create(String currencyCode, ObjectId contestId, List<AccountOp> accounts) {
        AccountingTranPrize accountingOp = findOne(contestId);
        if (accountingOp == null) {
            accountingOp = new AccountingTranPrize(currencyCode, contestId);
            accountingOp.accountOps = accounts;
            accountingOp.insertAndCommit();
        }
        return accountingOp;
    }
}
