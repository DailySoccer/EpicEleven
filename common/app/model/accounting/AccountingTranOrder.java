package model.accounting;

import model.Model;
import org.bson.types.ObjectId;

import java.util.List;

public class AccountingTranOrder extends AccountingTran {
    public ObjectId orderId;
    public String paymentId;

    public AccountingTranOrder() {}

    public AccountingTranOrder(String currencyCode, ObjectId orderId, String paymentId) {
        super(currencyCode, TransactionType.ORDER);
        this.orderId = orderId;
        this.paymentId = paymentId;
    }

    static public AccountingTranOrder findOne (String currencyCode, ObjectId orderId, String paymentId) {
        return Model.accountingTransactions()
                .findOne("{type: #, currencyCode: #, orderId: #, paymentId: #}", TransactionType.ORDER, currencyCode, orderId, paymentId)
                .as(AccountingTranOrder.class);
    }

    static public AccountingTran create(String currencyCode, ObjectId orderId, String paymentId, List<AccountOp> accounts) {
        AccountingTranOrder accountingOp = findOne(currencyCode, orderId, paymentId);
        if (accountingOp == null) {
            accountingOp = new AccountingTranOrder(currencyCode, orderId, paymentId);
            accountingOp.accountOps = accounts;
            accountingOp.insertAndCommit();
        }
        return accountingOp;
    }
}
