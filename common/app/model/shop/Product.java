package model.shop;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableList;
import model.User;
import org.joda.money.CurrencyUnit;
import org.joda.money.Money;
import utils.MoneyUtils;

import java.util.List;
import java.util.Map;

@JsonTypeInfo(use=JsonTypeInfo.Id.CLASS,property="_class")
public class Product {
    public enum ProductType {
        MONEY,
        SOCCER_PLAYER
    }

    public ProductType type;
    public String productId;
    public Money price;

    public Product () {}

    public Product (ProductType type, String productId, Money price) {
        this.type = type;
        this.productId = productId;
        this.price = price;
    }
}
