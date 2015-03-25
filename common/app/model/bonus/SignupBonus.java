package model.bonus;

import model.Model;
import org.joda.money.Money;

public class SignupBonus extends Bonus {
    public Money value;

    public SignupBonus() {
    }

    public SignupBonus(Money money) {
        super(BonusType.SIGNUP);
        this.value = money;
    }

    static public SignupBonus findOne() {
        return Model.bonus().findOne("{type: \"SIGNUP\"}").as(SignupBonus.class);
    }

    static public Money getMoney() {
        Money bonus = null;

        SignupBonus signupBonus = findOne();
        if (signupBonus != null && signupBonus.activated) {
            bonus = signupBonus.value;
        }

        return bonus;
    }

    static public SignupBonus create(boolean activated, Money money) {
        SignupBonus bonus = new SignupBonus(money);
        bonus.activated = activated;
        bonus.save();
        return bonus;
    }
}
