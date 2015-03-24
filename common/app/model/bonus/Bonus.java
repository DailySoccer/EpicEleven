package model.bonus;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import org.joda.money.Money;
import utils.MoneyUtils;

import java.math.RoundingMode;

@JsonTypeInfo(use=JsonTypeInfo.Id.CLASS,property="_class")
public class Bonus {
    static final Money MIN_ADD_FUNDS_FOR_BONUS = MoneyUtils.of(10);
    static final Double MULT_BONUS_BY_ADD_FUNDS = 1.0;

    public Money value;

    public Bonus (Money money) {
        this.value = money;
    }

    static public Bonus findOneBySignup() {
        Bonus bonus = null;

        return bonus;
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
