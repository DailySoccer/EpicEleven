package model;

import org.bson.types.ObjectId;
import org.jongo.marshall.jackson.oid.Id;
import utils.ListUtils;
import java.util.*;

public class UserNotification {

    public enum Topic {
        CONTEST_FINISHED,
        CONTEST_CANCELLED,
        MANAGER_LEVEL_UP,
        MANAGER_LEVEL_DOWN,
        ACHIEVEMENT_OWNED,

        CONTEST_NEXT_HOUR,
        CONTEST_WINNER;
    }

    @Id
    public ObjectId userNotificationId;
    public Topic topic;
    public Map<String, String> info;

    public Date createdAt;

    public UserNotification() {}

    public UserNotification(Topic topic) {
        this.createdAt = GlobalDate.getCurrentDate();
    }
}