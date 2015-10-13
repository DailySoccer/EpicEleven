package model;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableList;
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

    static public Product findOne(String productId) {
        return CatalogMap.get(productId);
    }

    static public Map<String, Product> CatalogMap = ImmutableMap.<String, Product>builder()
            .put("GOLD_1", new Product("GOLD_1", "product1", "images/icon-BuyGold1.png", Money.of(CurrencyUnit.EUR, 2.60), Money.of(MoneyUtils.CURRENCY_GOLD, 30), Money.of(MoneyUtils.CURRENCY_GOLD, 0), false))
            .put("GOLD_2", new Product("GOLD_2", "product2", "images/icon-BuyGold2.png", Money.of(CurrencyUnit.EUR, 4.20), Money.of(MoneyUtils.CURRENCY_GOLD, 55), Money.of(MoneyUtils.CURRENCY_GOLD, 5), true))
            .put("GOLD_3", new Product("GOLD_3", "product3", "images/icon-BuyGold3.png", Money.of(CurrencyUnit.EUR, 7.60), Money.of(MoneyUtils.CURRENCY_GOLD, 115), Money.of(MoneyUtils.CURRENCY_GOLD, 15), false))
            .put("GOLD_4", new Product("GOLD_4", "product4", "images/icon-BuyGold4.png", Money.of(CurrencyUnit.EUR, 12.90), Money.of(MoneyUtils.CURRENCY_GOLD, 250), Money.of(MoneyUtils.CURRENCY_GOLD, 50), false))
            .put("GOLD_5", new Product("GOLD_5", "product5", "images/icon-BuyGold5.png", Money.of(CurrencyUnit.EUR, 32), Money.of(MoneyUtils.CURRENCY_GOLD, 900), Money.of(MoneyUtils.CURRENCY_GOLD, 400), false))
            .put("GOLD_6", new Product("GOLD_6", "product6", "images/icon-BuyGold6.png", Money.of(CurrencyUnit.EUR, 99.95), Money.of(MoneyUtils.CURRENCY_GOLD, 1650), Money.of(MoneyUtils.CURRENCY_GOLD, 650), false))
            .put("ENERGY_1", new Product("ENERGY_1", "maxrefill", "images/icon-FullEnergy.png", Money.of(MoneyUtils.CURRENCY_GOLD, 30), Money.of(MoneyUtils.CURRENCY_ENERGY, User.MAX_ENERGY)))
            .build();

    static public List<Product> Catalog = ImmutableList.<Product>builder()
            .add(CatalogMap.get("GOLD_1"))
            .add(CatalogMap.get("GOLD_2"))
            .add(CatalogMap.get("GOLD_3"))
            .add(CatalogMap.get("GOLD_4"))
            .add(CatalogMap.get("GOLD_5"))
            .add(CatalogMap.get("GOLD_6"))
            .add(CatalogMap.get("ENERGY_1"))
            .build();

}
