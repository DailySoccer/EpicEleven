package model.accounting;

import org.bson.types.ObjectId;

public class AccountingOpOrder extends AccountingOps {
    public ObjectId orderId;
    public String paymentId;

    public AccountingOpOrder(ObjectId orderId, String paymentId) {
        super();
        this.orderId = orderId;
        this.paymentId = paymentId;
    }
}
