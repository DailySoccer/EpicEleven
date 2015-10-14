package model.shop;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import model.User;
import org.joda.money.CurrencyUnit;
import org.joda.money.Money;
import utils.MoneyUtils;

import java.util.List;
import java.util.Map;

public class Catalog {
    static public Product findOne(String productId) {
        return ProductsMap.get(productId);
    }

    static public Map<String, Product> ProductsMap = ImmutableMap.<String, Product>builder()
            .put("GOLD_1", new Product("GOLD_1", "product_gold_1", "images/icon-BuyGold1.png", Money.of(CurrencyUnit.EUR, 2.60), Money.of(MoneyUtils.CURRENCY_GOLD, 30), Money.of(MoneyUtils.CURRENCY_GOLD, 0), false))
            .put("GOLD_2", new Product("GOLD_2", "product_gold_2", "images/icon-BuyGold2.png", Money.of(CurrencyUnit.EUR, 4.20), Money.of(MoneyUtils.CURRENCY_GOLD, 55), Money.of(MoneyUtils.CURRENCY_GOLD, 5), true))
            .put("GOLD_3", new Product("GOLD_3", "product_gold_3", "images/icon-BuyGold3.png", Money.of(CurrencyUnit.EUR, 7.60), Money.of(MoneyUtils.CURRENCY_GOLD, 115), Money.of(MoneyUtils.CURRENCY_GOLD, 15), false))
            .put("GOLD_4", new Product("GOLD_4", "product_gold_4", "images/icon-BuyGold4.png", Money.of(CurrencyUnit.EUR, 12.90), Money.of(MoneyUtils.CURRENCY_GOLD, 250), Money.of(MoneyUtils.CURRENCY_GOLD, 50), false))
            .put("GOLD_5", new Product("GOLD_5", "product_gold_5", "images/icon-BuyGold5.png", Money.of(CurrencyUnit.EUR, 32), Money.of(MoneyUtils.CURRENCY_GOLD, 900), Money.of(MoneyUtils.CURRENCY_GOLD, 400), false))
            .put("GOLD_6", new Product("GOLD_6", "product_gold_6", "images/icon-BuyGold6.png", Money.of(CurrencyUnit.EUR, 99.95), Money.of(MoneyUtils.CURRENCY_GOLD, 1650), Money.of(MoneyUtils.CURRENCY_GOLD, 650), false))
            .put("ENERGY_1", new Product("ENERGY_1", "product_maxrefill", "images/icon-FullEnergy.png", Money.of(MoneyUtils.CURRENCY_GOLD, 30), Money.of(MoneyUtils.CURRENCY_ENERGY, User.MAX_ENERGY)))
            .build();

    static public List<Product> Products = ImmutableList.<Product>builder()
            .add(ProductsMap.get("GOLD_1"))
            .add(ProductsMap.get("GOLD_2"))
            .add(ProductsMap.get("GOLD_3"))
            .add(ProductsMap.get("GOLD_4"))
            .add(ProductsMap.get("GOLD_5"))
            .add(ProductsMap.get("GOLD_6"))
            .add(ProductsMap.get("ENERGY_1"))
            .build();

}
