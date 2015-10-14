package model.shop;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableList;
import model.User;
import org.joda.money.CurrencyUnit;
import org.joda.money.Money;
import utils.MoneyUtils;

import java.util.List;
import java.util.Map;

public class Product {
    public String id;
    public String name;
    public Money price;
    public Money gained;
    public Money free;
    public String imageUrl;
    public boolean mostPopular;

    public Product () {}

    public Product (String id, String name, String imageUrl, Money price, Money gained) {
        this.id = id;
        this.name = name;
        this.imageUrl = imageUrl;
        this.price = price;
        this.gained = gained;
        this.free = Money.zero(price.getCurrencyUnit());
        this.mostPopular = false;
    }

    public Product (String id, String name, String imageUrl, Money price, Money gained, Money free, boolean mostPopular) {
        this(id, name, imageUrl, price, gained);
        this.free = free;
        this.mostPopular = mostPopular;
    }
}
