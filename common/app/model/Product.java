package model;

import com.google.common.collect.ImmutableMap;
import java.util.Map;

import org.joda.money.CurrencyUnit;
import org.joda.money.Money;

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
        "PRODUCT_1", new Product("Product 1: Platinum", Money.of(CurrencyUnit.EUR, 10)),
        "PRODUCT_2", new Product("Product 2: Gold", Money.of(CurrencyUnit.EUR, 20))
    );
}
