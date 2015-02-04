package model.notification;

import model.GlobalDate;
import model.Model;
import org.bson.types.ObjectId;
import org.jongo.marshall.jackson.oid.Id;
import play.Logger;
import utils.ListUtils;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Date;
import java.util.List;


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

    public void markSent() {
        dateSent = GlobalDate.getCurrentDate();
        Model.notifications().insert(this);
    }

    public static boolean isNotSent(Topic topic, String reason, ObjectId recipientId) {
        return null == Model.notifications().findOne("{topic: #, reason: #, userId: #}", topic, getDigest(reason), recipientId).as(Notification.class);
    }

    public static Notification getNotification(Topic topic, ObjectId recipientId) {
        return Model.notifications().findOne("{topic: #, userId: #}", topic, recipientId).as(Notification.class);
    }

    public void updateNotification(String reason) {
        this.reason = reason;
        dateSent = GlobalDate.getCurrentDate();
        Model.notifications().update("{topic: #, userId: #}", topic, userId).upsert().with(this);
    }

    public static List<Notification> sentToList(Topic topic, String reason) {
        return ListUtils.asList(Model.notifications().find("{topic: #, reason: #}", topic, getDigest(reason)).as(Notification.class));
    }

    private static String getDigest(String original) {
        MessageDigest md = null;
        String result = null;
        try {
            md = MessageDigest.getInstance("MD5");
            md.update(original.getBytes());
            byte[] digest = md.digest();
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b & 0xff));
            }
            result = sb.toString();
        } catch (NoSuchAlgorithmException e) {
            Logger.error("WTF 1762");
        }

        return result;
    }

}
