package model;

import org.joda.money.Money;
import org.bson.types.ObjectId;
import utils.MoneyUtils;

import java.math.RoundingMode;

public class Bonus {
    static final Money MIN_ADD_FUNDS_FOR_BONUS = MoneyUtils.of(10);
    static final Double MULT_BONUS_BY_ADD_FUNDS = 1.0;

    public Money value;

    public Bonus (Money money) {
        this.value = money;
    }

    static public Bonus findOneByAddFunds(Money funds) {
        Bonus bonus = null;

        // Bonus por introducir dinero (>= MIN_MONEY_FOR_BONUS)
        if (!funds.isLessThan(MIN_ADD_FUNDS_FOR_BONUS)) {
            bonus = new Bonus(funds.multipliedBy(MULT_BONUS_BY_ADD_FUNDS, RoundingMode.FLOOR));
        }

        return bonus;
    }
}
