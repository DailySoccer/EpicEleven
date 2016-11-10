package model.rewards;

import org.joda.money.Money;

public class GoldReward extends Reward {
    public Money value;
    public int day = 1;

    public GoldReward() {
    }

    public GoldReward(Money value, int day) {
        super(RewardType.GOLD);
        this.value = value;
        this.day = day;
    }

    public String debug() {
        return "[" + super.debug() + " " + value.toString() + "]";
    }
}
