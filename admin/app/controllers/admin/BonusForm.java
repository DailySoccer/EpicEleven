package controllers.admin;

import model.bonus.AddFundsBonus;
import model.bonus.SignupBonus;
import play.data.validation.Constraints;

import java.math.BigDecimal;

public class BonusForm {
    public boolean signupBonus_activated;
    public BigDecimal signupBonus_gold = new BigDecimal(0);
    public BigDecimal signupBonus_manager = new BigDecimal(0);

    public boolean addFundsBonus_activated;
    public BigDecimal addFundsBonus_minMoney = new BigDecimal(0);
    public BigDecimal addFundsBonus_maxMoney = new BigDecimal(0);
    public int addFundsBonus_percent;

    public BonusForm () {
    }

    public BonusForm (SignupBonus signupBonus, AddFundsBonus addFundsBonus) {
        if (signupBonus != null) {
            signupBonus_activated = signupBonus.activated;
            signupBonus_gold = signupBonus.gold.getAmount();
            signupBonus_manager = signupBonus.manager.getAmount();
        }

        if (addFundsBonus != null) {
            addFundsBonus_activated = addFundsBonus.activated;
            addFundsBonus_minMoney = addFundsBonus.minMoney.getAmount();
            addFundsBonus_maxMoney = addFundsBonus.maxMoney.getAmount();
            addFundsBonus_percent = addFundsBonus.percent;
        }
    }
}
