package model;

import java.util.HashMap;
import java.util.Map;

public class Product {
    enum ProductType {
        PRODUCT_1,
        PRODUCT_2
    }
    static Map<String, Product> productsMap = new HashMap<String, Product>() {
        {
            put(ProductType.PRODUCT_1.toString(), new Product("Product 1: Platinum", 10));
            put(ProductType.PRODUCT_2.toString(), new Product("Product 2: Gold", 20));
        }
    };

    public String name;
    public int price;

    public Product (String name, int price) {
        this.name = name;
        this.price = price;
    }

    static public Product findOne(String productType) {
        return productsMap.get(productType);
    }
}
