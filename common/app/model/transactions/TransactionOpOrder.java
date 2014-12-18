package model.transactions;

import org.bson.types.ObjectId;

public class TransactionOpOrder extends TransactionOps {
    public ObjectId orderId;
    public String paymentId;

    public TransactionOpOrder(ObjectId orderId, String paymentId) {
        super();
        this.orderId = orderId;
        this.paymentId = paymentId;
    }
}
