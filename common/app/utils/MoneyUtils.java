package utils;

import model.Product;
import org.joda.money.Money;

public class MoneyUtils {
    static public Money withCurrencyUnit(Money aMoney) {
        return aMoney.withCurrencyUnit(Product.CURRENCY_DEFAULT);
    }

    static public int compareTo(Money aMoney, Money otherMoney) {
        return aMoney.withCurrencyUnit(Product.CURRENCY_DEFAULT).compareTo(otherMoney.withCurrencyUnit(Product.CURRENCY_DEFAULT));
    }

    static public Money plus(Money aMoney, Money amount) {
        return aMoney.withCurrencyUnit(Product.CURRENCY_DEFAULT).plus(amount.withCurrencyUnit(Product.CURRENCY_DEFAULT));
    }

    static public boolean isGreaterThan(Money aMoney, Money amount) {
        return aMoney.withCurrencyUnit(Product.CURRENCY_DEFAULT).isGreaterThan(amount.withCurrencyUnit(Product.CURRENCY_DEFAULT));
    }

    static public boolean equals(Money aMoney, Money amount) {
        return aMoney.withCurrencyUnit(Product.CURRENCY_DEFAULT).compareTo(amount.withCurrencyUnit(Product.CURRENCY_DEFAULT)) == 0;
    }
}
