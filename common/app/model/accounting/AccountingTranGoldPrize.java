package model.accounting;

import model.Model;
import org.bson.types.ObjectId;

import java.util.List;

public class AccountingTranGoldPrize extends AccountingTran {
    public ObjectId contestId;

    public AccountingTranGoldPrize() {}

    public AccountingTranGoldPrize(ObjectId contestId) {
        super(TransactionType.GOLD_PRIZE);
        this.contestId = contestId;
    }

    static public AccountingTranPrize findOne (ObjectId contestId) {
        return Model.accountingTransactions()
                .findOne("{type: #, contestId: #}", TransactionType.GOLD_PRIZE, contestId)
                .as(AccountingTranPrize.class);
    }

    static public AccountingTran create(ObjectId contestId, List<AccountOp> accounts) {
        AccountingTranPrize accountingOp = findOne(contestId);
        if (accountingOp == null) {
            accountingOp = new AccountingTranPrize(contestId);
            accountingOp.accountOps = accounts;
            accountingOp.insertAndCommit();
        }
        return accountingOp;
    }
}
