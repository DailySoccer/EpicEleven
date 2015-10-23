package model.shop;

import model.Model;
import org.bson.types.ObjectId;
import org.joda.money.Money;

public class ProductSoccerPlayer extends Product {
    public ObjectId contestId;

    public ProductSoccerPlayer() {}

    public ProductSoccerPlayer(Money price, ObjectId contestId, ObjectId templateSoccerPlayerId) {
        super(ProductType.SOCCER_PLAYER, templateSoccerPlayerId.toString(), price);
        this.contestId = contestId;
    }

    public Order findOrder() {
        return Model.orders().findOne("{state : #, 'product.type': #, 'product.productId': #, 'product.contestId': #}",
                Order.State.COMPLETED, type, productId, contestId)
                .as(Order.class);
    }
}
