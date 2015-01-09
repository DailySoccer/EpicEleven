package model.accounting;

import com.mongodb.WriteResult;
import model.Model;
import org.bson.types.ObjectId;

import java.util.List;

public class AccountingOpOrder extends AccountingOp {
    public ObjectId orderId;
    public String paymentId;

    public AccountingOpOrder() {}

    public AccountingOpOrder(ObjectId orderId, String paymentId) {
        super(TransactionType.ORDER);
        this.orderId = orderId;
        this.paymentId = paymentId;
    }

    static public AccountingOpOrder findOne (ObjectId orderId, String paymentId) {
        return Model.accountingTransactions()
                .findOne("{type: #, orderId: #, paymentId: #}", TransactionType.ORDER, orderId, paymentId)
                .as(AccountingOpOrder.class);
    }

    static public AccountingOp create(ObjectId orderId, String paymentId, List<AccountOp> accounts) {
        AccountingOpOrder accountingOp = findOne(orderId, paymentId);
        if (accountingOp == null) {
            accountingOp = new AccountingOpOrder(orderId, paymentId);
            accountingOp.accounts = accounts;
            accountingOp.insert();
        }
        return accountingOp;
    }
}
