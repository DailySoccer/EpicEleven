package model.accounting;


import model.Model;
import org.bson.types.ObjectId;

import java.util.List;

public class AccountingTranRefund extends AccountingTran {
    public ObjectId refundId;

    public AccountingTranRefund() {}

    public AccountingTranRefund(ObjectId refundId) {
        super(TransactionType.REFUND);
        this.refundId = refundId;
    }

    static public AccountingTranRefund findOne (ObjectId refundId) {
        return Model.accountingTransactions()
                .findOne("{type: #, refundId: #}", TransactionType.REFUND, refundId)
                .as(AccountingTranRefund.class);
    }

    static public AccountingTran create(ObjectId refundId, List<AccountOp> accounts) {
        AccountingTranRefund accountingOp = findOne(refundId);
        if (accountingOp == null) {
            accountingOp = new AccountingTranRefund(refundId);
            accountingOp.accountOps = accounts;
            accountingOp.insertAndCommit();
        }
        return accountingOp;
    }
}
