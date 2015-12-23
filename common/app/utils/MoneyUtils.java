package utils;

import org.joda.money.CurrencyUnit;
import org.joda.money.Money;

public class MoneyUtils {
    static public CurrencyUnit CURRENCY_REAL    = CurrencyUnit.EUR;

    static public CurrencyUnit CURRENCY_GOLD    = CurrencyUnit.AUD;
    static public CurrencyUnit CURRENCY_MANAGER = CurrencyUnit.CHF;
    static public CurrencyUnit CURRENCY_ENERGY  = CurrencyUnit.JPY;
    static public CurrencyUnit CURRENCY_DEFAULT = CURRENCY_GOLD;

    static public Money zero = Money.zero(CURRENCY_DEFAULT);
    static public Money zero(String currencyUnit) { return Money.zero(CurrencyUnit.of(currencyUnit)); };

    static public String asString(String money) {
        return asString(Money.parse(money));
    }

    static public String asString(Money money) {
        if (money.getCurrencyUnit().equals(CURRENCY_GOLD)) {
            return "@ " + money.getAmount().toBigInteger();
        }
        else if (money.getCurrencyUnit().equals(CURRENCY_MANAGER)) {
            return "¥ " + money.getAmount().toBigInteger();
        }
        else if (money.getCurrencyUnit().equals(CURRENCY_ENERGY)) {
            return "ƒ " + money.getAmount().toBigInteger();
        }
        return String.valueOf(money);
    }

    static public int compareTo(Money aMoney, Money otherMoney) {
        return aMoney.withCurrencyUnit(CURRENCY_DEFAULT).compareTo(otherMoney.withCurrencyUnit(CURRENCY_DEFAULT));
    }

    static public boolean isGreaterThan(Money aMoney, Money amount) {
        return aMoney.withCurrencyUnit(CURRENCY_DEFAULT).isGreaterThan(amount.withCurrencyUnit(CURRENCY_DEFAULT));
    }

    static public boolean equals(Money aMoney, Money amount) {
        return aMoney.withCurrencyUnit(CURRENCY_DEFAULT).compareTo(amount.withCurrencyUnit(CURRENCY_DEFAULT)) == 0;
    }

    static public boolean isGold(Money aMoney) {
        return aMoney.getCurrencyUnit().equals(CURRENCY_GOLD);
    }

    static public boolean isManager(Money aMoney) {
        return aMoney.getCurrencyUnit().equals(CURRENCY_MANAGER);
    }

    static public boolean isEnergy(Money aMoney) {
        return aMoney.getCurrencyUnit().equals(CURRENCY_ENERGY);
    }
}
