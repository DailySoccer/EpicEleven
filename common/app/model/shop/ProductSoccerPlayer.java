package model.shop;

import org.bson.types.ObjectId;
import org.joda.money.Money;

public class ProductSoccerPlayer extends Product {
    public ObjectId contestId;

    public ProductSoccerPlayer() {}

    public ProductSoccerPlayer(Money price, ObjectId contestId, ObjectId templateSoccerPlayerId) {
        super(ProductType.SOCCER_PLAYER, templateSoccerPlayerId.toString(), price);
        this.contestId = contestId;
    }
}
