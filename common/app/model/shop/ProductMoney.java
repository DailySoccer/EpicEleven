package model.shop;

import org.joda.money.Money;

public class ProductMoney extends Product {
    public String name;
    public Money gained;
    public Money free;
    public String imageUrl;
    public boolean mostPopular;

    public ProductMoney () {}

    public ProductMoney (String productId, Money price, String name, String imageUrl, Money gained) {
        super(ProductType.MONEY, productId, price);
        this.name = name;
        this.imageUrl = imageUrl;
        this.gained = gained;
        this.free = Money.zero(price.getCurrencyUnit());
        this.mostPopular = false;
    }

    public ProductMoney (String productId, Money price, String name, String imageUrl, Money gained, Money free, boolean mostPopular) {
        this(productId, price, name, imageUrl, gained);
        this.free = free;
        this.mostPopular = mostPopular;
    }
}
