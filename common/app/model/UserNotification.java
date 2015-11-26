package model;

import org.bson.types.ObjectId;
import org.jongo.marshall.jackson.oid.Id;
import utils.ListUtils;
import java.util.*;

public class UserNotification implements JongoId {

    public enum Topic {
        CONTEST_FINISHED,
        CONTEST_CANCELLED,
        MANAGER_LEVEL_UP,
        MANAGER_LEVEL_DOWN,
        ACHIEVEMENT_EARNED
    }

    @Id
    public ObjectId userNotificationId;
    public Topic topic;
    public Map<String, String> info;

    public Date createdAt;

    public UserNotification(Topic topic) {
        this.topic = topic;
        this.createdAt = GlobalDate.getCurrentDate();
    }

    public UserNotification(Topic topic, Map<String, String> info) {
        this(topic);
        this.info = info;
    }

    public ObjectId getId() {
        return userNotificationId;
    }

    public UserNotification() {}

    public void sendTo(ObjectId userId) {
        Model.users().update("{_id: #}", userId).with("{$addToSet: {notifications: #}}", this);
    }

    public void sendTo(List<ObjectId> userIds) {
        userIds.forEach( this::sendTo );
    }

    public static UserNotification contestFinished(Contest contest) {
        return new UserNotification(Topic.CONTEST_FINISHED, new HashMap<String, String>() {{
            put("contestId", contest.contestId.toString());
        }});
    }

    public static UserNotification contestCancelled(Contest contest) {
        return new UserNotification(Topic.CONTEST_CANCELLED, new HashMap<String, String>() {{
            put("contestId", contest.contestId.toString());
        }});
    }

    public static UserNotification managerLevelUp(int level) {
        return new UserNotification(Topic.MANAGER_LEVEL_UP, new HashMap<String, String>() {{
            put("level", String.valueOf(level));
        }});
    }

    public static UserNotification managerLevelDown(int level) {
        return new UserNotification(Topic.MANAGER_LEVEL_DOWN, new HashMap<String, String>() {{
            put("level", String.valueOf(level));
        }});
    }

    public static UserNotification achievementEarned(AchievementType achievementType) {
        return new UserNotification(Topic.ACHIEVEMENT_EARNED, new HashMap<String, String>() {{
            put("achievement", achievementType.toString());
        }});
    }
}
