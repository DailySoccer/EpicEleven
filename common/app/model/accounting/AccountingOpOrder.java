package model.accounting;

import com.mongodb.WriteResult;
import model.Model;
import org.bson.types.ObjectId;

import java.util.List;

public class AccountingOpOrder extends AccountingOp {
    public ObjectId orderId;
    public String paymentId;

    public AccountingOpOrder() {
    }

    public AccountingOpOrder(ObjectId orderId, String paymentId) {
        super(AccountingOp.TransactionType.ORDER);
        this.orderId = orderId;
        this.paymentId = paymentId;
    }

    static public AccountingOp create(ObjectId orderId, String paymentId, List<AccountOp> accounts) {
        AccountingOpOrder accountingOp = new AccountingOpOrder(orderId, paymentId);
        accountingOp.accounts = accounts;
        WriteResult result = Model.accountingTransactions().update("{type: #, orderId: #, paymentId: #}",
                accountingOp.type, accountingOp.orderId, accountingOp.paymentId).upsert().with(accountingOp);
        if (result.getN() > 0) {
            play.Logger.info(accountingOp.toJson());
        }
        return accountingOp;
    }
}
