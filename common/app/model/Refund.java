package model;

import org.bson.types.ObjectId;

public class Refund {
    public enum State {
        PENDING,
        PROCESSING,
        COMPLETED
    }

    public ObjectId userId;
    public State state;
    public int price;

    public Refund() {}

    public Refund(ObjectId userId, int price) {
        this.userId = userId;
        this.state = State.PENDING;
        this.price = price;
    }

    public void insert() {
        Model.refunds().insert(this);
    }
}
