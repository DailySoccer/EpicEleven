package model.accounting;

import com.mongodb.WriteResult;
import model.Model;
import org.bson.types.ObjectId;

public class AccountingOpsOrder extends AccountingOps {
    public ObjectId orderId;
    public String paymentId;

    public AccountingOpsOrder(ObjectId orderId, String paymentId) {
        super();
        this.orderId = orderId;
        this.paymentId = paymentId;
    }

    static public AccountingOp create(AccountingOpsOrder changes) {
        AccountingOp accountingOp = new AccountingOp(AccountingOp.TransactionType.ORDER);
        accountingOp.changes = changes;
        WriteResult result = Model.accountingTransactions().update("{type: #, 'changes.orderId': #, 'changes.paymentId': #}",
                accountingOp.type, changes.orderId, changes.paymentId).upsert().with(accountingOp);
        if (result.getN() > 0) {
            play.Logger.info(accountingOp.toJson());
        }
        return accountingOp;
    }
}
