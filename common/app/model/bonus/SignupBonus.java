package model.bonus;

import model.Model;
import org.joda.money.Money;

public class SignupBonus extends Bonus {
    public Money gold;
    public Money manager;

    public SignupBonus() {
    }

    public SignupBonus(Money gold, Money manager) {
        super(BonusType.SIGNUP);
        this.gold = gold;
        this.manager = manager;
    }

    static public SignupBonus findOne() {
        return Model.bonus().findOne("{type: \"SIGNUP\"}").as(SignupBonus.class);
    }

    static public SignupBonus create(boolean activated, Money gold, Money manager) {
        SignupBonus bonus = new SignupBonus(gold, manager);
        bonus.activated = activated;
        bonus.save();
        return bonus;
    }
}
