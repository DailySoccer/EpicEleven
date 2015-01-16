package model.accounting;

import model.Model;
import org.bson.types.ObjectId;

import java.util.List;

public class AccountingTranOrder extends AccountingTran {
    public ObjectId orderId;
    public String paymentId;

    public AccountingTranOrder() {}

    public AccountingTranOrder(ObjectId orderId, String paymentId) {
        super(TransactionType.ORDER);
        this.orderId = orderId;
        this.paymentId = paymentId;
    }

    static public AccountingTranOrder findOne (ObjectId orderId, String paymentId) {
        return Model.accountingTransactions()
                .findOne("{type: #, orderId: #, paymentId: #}", TransactionType.ORDER, orderId, paymentId)
                .as(AccountingTranOrder.class);
    }

    static public AccountingTran create(ObjectId orderId, String paymentId, List<AccountOp> accounts) {
        AccountingTranOrder accountingOp = findOne(orderId, paymentId);
        if (accountingOp == null) {
            accountingOp = new AccountingTranOrder(orderId, paymentId);
            accountingOp.accountOps = accounts;
            accountingOp.insertAndCommit();
        }
        return accountingOp;
    }
}
