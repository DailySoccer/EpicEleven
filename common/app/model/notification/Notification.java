package model.notification;

import com.google.common.collect.ImmutableBiMap;
import com.google.common.collect.ImmutableMap;
import model.Contest;
import model.ContestEntry;
import model.GlobalDate;
import model.Model;
import org.bson.types.ObjectId;
import org.jongo.marshall.jackson.oid.Id;
import utils.ListUtils;

import java.util.*;
import java.util.stream.Collectors;


public class Notification {

    public enum Topic {
        CONTEST_NEXT_HOUR,
        CONTEST_WINNER;
    }

    @Id
    public ObjectId notificationId;
    public Topic topic;
    public String reason;
    public ObjectId recipientId;

    public Date createdAt;
    public Date dateSent;

    public Notification() {}

    public Notification(Topic topic, String reason, ObjectId recipientId) {
        this.topic = topic;
        this.reason = reason;
        this.recipientId = recipientId;
        this.createdAt = GlobalDate.getCurrentDate();
    }

    public void insert() {
        Model.notifications().insert(this);
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
