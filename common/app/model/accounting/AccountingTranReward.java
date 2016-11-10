package model.accounting;

import model.Model;
import org.bson.types.ObjectId;

import java.util.List;

public class AccountingTranReward extends AccountingTran {
    // Identificador de reward mediante el que podremos saber si ya se ha aplicado o no
    public ObjectId rewardId;

    public AccountingTranReward() {}

    public AccountingTranReward(String currencyCode, ObjectId rewardId) {
        super(currencyCode, TransactionType.REWARD);
        this.rewardId = rewardId;
    }

    static public model.accounting.AccountingTranReward findOne (ObjectId rewardId) {
        return Model.accountingTransactions()
                .findOne("{type: #, rewardId: #}", TransactionType.REWARD, rewardId)
                .as(model.accounting.AccountingTranReward.class);
    }

    static public AccountingTran create(String currencyCode, ObjectId rewardId, List<AccountOp> accounts) {
        model.accounting.AccountingTranReward accountingOp = findOne(rewardId);
        if (accountingOp == null) {
            accountingOp = new model.accounting.AccountingTranReward(currencyCode, rewardId);
            accountingOp.accountOps = accounts;
            accountingOp.insertAndCommit();
        }
        return accountingOp;
    }
}
