package model.shop;

import model.Model;
import org.bson.types.ObjectId;
import org.joda.money.Money;
import org.jongo.marshall.jackson.oid.Id;

import java.util.List;

public class Order {
    static final String REFERER_URL_DEFAULT = "epiceleven.com";

    public enum TransactionType {
        IN_GAME,
        PAYPAL,
        MORE_THAN_GAME
    }

    public enum State {
        WAITING_APPROVAL,
        WAITING_PAYMENT,
        PENDING,
        COMPLETED,
        CANCELED
    }

    @Id
    public ObjectId orderId;

    public TransactionType transactionType;
    public ObjectId userId;
    public State state;
    public String reason;

    public String paymentId;
    public String payerId;
    public String referer;

    public List<Product> products;

    public Order() {
    }

    public Order(ObjectId orderId, ObjectId userId, TransactionType transactionType, String paymentId, List<Product> products) {
        this.orderId = orderId;
        this.transactionType = transactionType;
        this.userId = userId;
        this.state = State.WAITING_APPROVAL;
        this.paymentId = paymentId;
        this.products = products;
    }

    public void setWaitingPayment(String payerId) {
        this.state = State.WAITING_PAYMENT;
        this.payerId = payerId;
        Model.orders().update(orderId).with("{$set: {state: #, payerId: #}}", this.state, this.payerId);
    }

    public void setPending() {
        this.state = State.PENDING;
        Model.orders().update(orderId).with("{$set: {state: #}}", this.state);
    }

    public void setCompleted() {
        this.state = State.COMPLETED;
        Model.orders().update(orderId).with("{$set: {state: #}}", this.state);
    }

    public void setCanceled() {
        this.state = State.CANCELED;
        Model.orders().update(orderId).with("{$set: {state: #}}", this.state);
    }

    public boolean isPending() {
        return state.equals(State.PENDING);
    }

    public boolean isCompleted() {
        return state.equals(State.COMPLETED);
    }

    public boolean isCanceled() {
        return state.equals(State.CANCELED);
    }

    public Money price() {
        Money result = null;
        for(Product product : products) {
            if (result == null) {
                result = product.price;
            }
            else {
                result.plus(product.price);
            }
        }
        return result;
    }

    static public Order findOne(String orderId) {
        return ObjectId.isValid(orderId) ? Model.orders().findOne("{_id : #}", new ObjectId(orderId)).as(Order.class) : null;
    }

    static public Order findOneFromPayment(String paymentId) {
        return Model.orders().findOne("{paymentId : #}", paymentId).as(Order.class);
    }

    static public Order create (ObjectId orderId, ObjectId userId, TransactionType transactionType, String paymentId, List<Product> products, String refererUrl) {
        Order order = new Order(orderId, userId, transactionType, paymentId, products);
        // No almacenamos el referer si es el "por defecto"
        if (!refererUrl.contains(REFERER_URL_DEFAULT)) {
            order.referer = refererUrl;
        }
        Model.orders().insert(order);
        return order;
    }
}
