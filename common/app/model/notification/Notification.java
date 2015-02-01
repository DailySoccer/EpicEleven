package model.notification;

import model.GlobalDate;
import model.Model;
import org.bson.types.ObjectId;
import org.jongo.marshall.jackson.oid.Id;
import play.Logger;

import java.security.NoSuchAlgorithmException;
import java.util.Date;
import java.security.MessageDigest;


public class Notification {

    public enum State {
        READY,
        SENT
    }

    public enum Topic {
        CONTEST_NEXT_HOUR,
        CONTEST_WINNER;
    }

    @Id
    public ObjectId notificationId;
    public Topic topic;
    public String reason;
    public ObjectId userId;
    public State state;
    public Date createdAt;
    public Date dateSent;

    public Notification() {
        state = State.READY;
    }

    public Notification(Topic topic, String reason, ObjectId recipientId) {
        this.state = State.READY;
        this.topic = topic;
        this.reason = getDigest(reason);
        this.userId = recipientId;
        this.createdAt = GlobalDate.getCurrentDate();
    }

    public void markSent() {
        state = State.SENT;
        dateSent = GlobalDate.getCurrentDate();
        Model.notifications().update(this.notificationId).with(this);
    }

    public static boolean isNotSent(Topic topic, String reason, ObjectId recipientId) {
        return null == Model.notifications().findOne("{topic: #, reason: #, userId: #, state: \"SENT\" }", topic, getDigest(reason), recipientId).as(Notification.class);
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
