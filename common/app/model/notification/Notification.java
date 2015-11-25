package model.notification;

import model.GlobalDate;
import model.Model;
import org.bson.types.ObjectId;
import org.jongo.marshall.jackson.oid.Id;
import utils.ListUtils;

import java.util.Date;
import java.util.List;
import java.util.Map;


public class Notification {

    public enum Topic {
        CONTEST_FINISHED,
        DUEL_FINISHED,
        CONTEST_CANCELLED,
        MANAGER_LEVEL_UP,
        MANAGER_LEVEL_DOWN,
        ACHIEVEMENT_OWNED,

        CONTEST_NEXT_HOUR,
        CONTEST_WINNER;
    }

    @Id
    public ObjectId notificationId;
    public Topic topic;
    public String reason;
    public ObjectId userId;
    public Map<String, String> info;
    public boolean readed;

    public Date createdAt;
    public Date dateSent;

    public Notification() {}

    public Notification(Topic topic, String reason, ObjectId recipientId) {
        this.topic = topic;
        this.reason = reason;
        this.userId = recipientId;
        this.createdAt = GlobalDate.getCurrentDate();
    }

    public Notification(Topic topic, String reason, ObjectId recipientId, Map info) {
        this(topic, reason, recipientId);
        this.info = info;
    }

    public void insertAsSent() {
        dateSent = GlobalDate.getCurrentDate();
        Model.notifications().insert(this);
    }

    public void updateAsSent() {
        dateSent = GlobalDate.getCurrentDate();
        Model.notifications().update("{_id: #}", notificationId).with(this);
    }


    public static Notification findLastNotification(Topic topic, ObjectId recipientId) {
        Iterable<Notification> notifications = Model.notifications().find("{topic: #, userId: #}", topic, recipientId).sort("{createdAt: -1}").limit(1).as(Notification.class);
        return notifications.iterator().hasNext()? notifications.iterator().next(): null;
    }

    public static Notification findLastNotification(Topic topic) {
        Iterable<Notification> notifications = Model.notifications().find("{topic: #}", topic).sort("{createdAt: -1}").limit(1).as(Notification.class);
        return notifications.iterator().hasNext()? notifications.iterator().next(): null;
    }

    private static String getDigest(String original) {
        return original==null? null : String.valueOf(original.hashCode());
    }

    public static List<Notification> findUnsentNotifications(Topic topic) {
        return ListUtils.asList(Model.notifications().find("{topic: #, dateSent:{ $exists: false }}", topic).as(Notification.class));
    }

}
