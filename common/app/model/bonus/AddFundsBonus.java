package model.bonus;

import model.Model;
import org.joda.money.Money;

import java.math.RoundingMode;

public class AddFundsBonus extends Bonus {
    public Money minMoney;
    public Money maxMoney;
    public float multiplier;

    public AddFundsBonus() {
    }

    public AddFundsBonus(Money minMoney, Money maxMoney, float multiplier) {
        super(BonusType.ADD_FUNDS);
        this.minMoney = minMoney;
        this.maxMoney = maxMoney;
        this.multiplier = multiplier;
    }

    static public AddFundsBonus findOne() {
        return Model.bonus().findOne("{type: \"ADD_FUNDS\"}").as(AddFundsBonus.class);
    }

    static public Money getMoney(Money funds) {
        Money bonus = null;

        AddFundsBonus addFundsBonus = findOne();
        if (addFundsBonus != null && addFundsBonus.activated) {
            if (!funds.isLessThan(addFundsBonus.minMoney)) {
                if (funds.isGreaterThan(addFundsBonus.maxMoney)) {
                    funds = addFundsBonus.maxMoney;
                }
                bonus = funds.multipliedBy(addFundsBonus.multiplier, RoundingMode.FLOOR);
            }
        }

        return bonus;
    }

    static public AddFundsBonus create(boolean activated, Money minMoney, Money maxMoney, float multiplier) {
        AddFundsBonus bonus = new AddFundsBonus(minMoney, maxMoney, multiplier);
        bonus.activated = activated;
        bonus.save();
        return bonus;
    }
}

