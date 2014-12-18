package model;

public enum Product {
    PRODUCT_1   ("Product 1: Platinum", 10),
    PRODUCT_2   ("Product 2: Gold", 20);

    public final String name;
    public final int price;

    Product (String name, int price) {
        this.name = name;
        this.price = price;
    }

    static public Product findOne(String productId) {
        return Product.valueOf(productId);
    }
}
