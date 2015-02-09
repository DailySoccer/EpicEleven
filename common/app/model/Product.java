package model;

import com.google.common.collect.ImmutableMap;
import java.util.Map;

public class Product {
    public String name;
    public int price;

    public Product () {}

    public Product (String name, int price) {
        this.name = name;
        this.price = price;
    }

    static public Product findOne(String productId) {
        return catalog.get(productId);
    }

    static private Map<String, Product> catalog = ImmutableMap.of(
        "PRODUCT_1", new Product("Product 1: Platinum", 10),
        "PRODUCT_2", new Product("Product 2: Gold", 20)
    );
}
