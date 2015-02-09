package model.notification;

import model.GlobalDate;
import model.Model;
import org.bson.types.ObjectId;
import org.jongo.marshall.jackson.oid.Id;

import java.util.Date;


public class Notification {

    public enum Topic {
        CONTEST_NEXT_HOUR,
        CONTEST_WINNER;
    }

    @Id
    public ObjectId notificationId;
    public Topic topic;
    public String reason;
    public ObjectId userId;
    public Date createdAt;
    public Date dateSent;

    public Notification() {}

    public Notification(Topic topic, String reason, ObjectId recipientId) {
        this.topic = topic;
        this.reason = getDigest(reason);
        this.userId = recipientId;
        this.createdAt = GlobalDate.getCurrentDate();
    }

    public void insertAsSent() {
        dateSent = GlobalDate.getCurrentDate();
        Model.notifications().insert(this);
    }

    public static Notification getLastNotification(Topic topic, ObjectId recipientId) {
        Iterable<Notification> notifications = Model.notifications().find("{topic: #, userId: #}", topic, recipientId).sort("{createdAt: -1}").limit(1).as(Notification.class);
        return notifications.iterator().hasNext()? notifications.iterator().next(): null;
    }


    private static String getDigest(String original) {
        return original==null? null : String.valueOf(original.hashCode());
    }

}
