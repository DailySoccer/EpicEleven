package model.accounting;


import model.Model;
import org.bson.types.ObjectId;

import java.util.List;

public class AccountingTranRefund extends AccountingTran {
    public ObjectId refundId;

    public AccountingTranRefund() {}

    public AccountingTranRefund(String currencyCode, ObjectId refundId) {
        super(currencyCode, TransactionType.REFUND);
        this.refundId = refundId;
    }

    static public AccountingTranRefund findOne (ObjectId refundId) {
        return Model.accountingTransactions()
                .findOne("{type: #, refundId: #}", TransactionType.REFUND, refundId)
                .as(AccountingTranRefund.class);
    }

    static public AccountingTran create(String currencyCode, ObjectId refundId, List<AccountOp> accounts) {
        AccountingTranRefund accountingOp = findOne(refundId);
        if (accountingOp == null) {
            accountingOp = new AccountingTranRefund(currencyCode, refundId);
            accountingOp.accountOps = accounts;
            accountingOp.insertAndCommit();
        }
        return accountingOp;
    }
}
