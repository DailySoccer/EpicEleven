package model.rewards;

import org.joda.money.Money;

public class GoldReward extends Reward {
    public Money value;

    public GoldReward() {
    }

    public GoldReward(Money value) {
        super(RewardType.GOLD);
        this.value = value;
    }

    public String debug() {
        return "[" + super.debug() + " " + value.toString() + "]";
    }
}
