package model.bonus;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import model.Model;

@JsonTypeInfo(use=JsonTypeInfo.Id.CLASS,property="_class")
public class Bonus {
    @JsonIgnore
    public static final Double MULT_BONUS_TO_CASH = 0.04;

    enum BonusType {
        SIGNUP,
        ADD_FUNDS
    }

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
