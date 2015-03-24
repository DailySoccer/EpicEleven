package model.bonus;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import model.Model;
import org.joda.money.Money;
import utils.MoneyUtils;

import java.math.RoundingMode;

@JsonTypeInfo(use=JsonTypeInfo.Id.CLASS,property="_class")
public class Bonus {
    enum BonusType {
        SIGNUP,
        ADD_FUNDS
    }

    static final Money MIN_ADD_FUNDS_FOR_BONUS = MoneyUtils.of(10);
    static final Double MULT_BONUS_BY_ADD_FUNDS = 1.0;

    public boolean activated = false;
    public BonusType type;

    public Bonus () {
    }

    public Bonus (BonusType type) {
        this.type = type;
    }

    public void save() {
        Model.bonus().update("{type: #}", type).upsert().with(this);
    }
}
