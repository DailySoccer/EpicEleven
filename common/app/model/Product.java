package model;

import com.google.common.collect.ImmutableMap;
import org.joda.money.CurrencyUnit;
import org.joda.money.Money;
import utils.MoneyUtils;

import java.util.Map;

public class Product {
    public String name;
    public Money price;

    public Product () {}

    public Product (String name, Money price) {
        this.name = name;
        this.price = price;
    }

    static public Product findOne(String productId) {
        return catalog.get(productId);
    }

    static private Map<String, Product> catalog = ImmutableMap.of(
    );
}
