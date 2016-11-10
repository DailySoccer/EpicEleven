package model.rewards;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import org.bson.types.ObjectId;
import org.jongo.marshall.jackson.oid.Id;

@JsonTypeInfo(use=JsonTypeInfo.Id.CLASS, property="_class")
public class Reward {
    public enum RewardType {
        GOLD
    }

    @Id
    public ObjectId rewardId;

    public RewardType type;

    protected Reward() {
    }

    protected Reward(RewardType type) {
        this.rewardId = new ObjectId();
        this.type = type;
    }

    protected String debug() {
        return type.toString() + "(" + rewardId.toString() + ")";
    }
}
