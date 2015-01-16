package model.accounting;

import model.Model;
import org.bson.types.ObjectId;

import java.util.List;

public class AccountingOpPrize extends AccountingOp {
    public ObjectId contestId;

    public AccountingOpPrize() {}

    public AccountingOpPrize(ObjectId contestId) {
        super(TransactionType.PRIZE);
        this.contestId = contestId;
    }

    static public AccountingOpPrize findOne (ObjectId contestId) {
        return Model.accountingTransactions()
                .findOne("{type: #, contestId: #}", TransactionType.PRIZE, contestId)
                .as(AccountingOpPrize.class);
    }

    static public AccountingOp create(ObjectId contestId, List<AccountOp> accounts) {
        AccountingOpPrize accountingOp = findOne(contestId);
        if (accountingOp == null) {
            accountingOp = new AccountingOpPrize(contestId);
            accountingOp.accountOps = accounts;
            accountingOp.insert();
        }
        return accountingOp;
    }
}
