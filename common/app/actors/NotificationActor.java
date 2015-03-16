package actors;

import model.Contest;
import model.ContestEntry;
import model.Model;
import model.User;
import model.notification.MessageTemplateSend;
import model.notification.Notification;
import model.notification.Notification.Topic;
import org.bson.types.ObjectId;
import org.joda.time.DateTime;
import play.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


public class NotificationActor extends TickableActor {

    @Override public void onReceive(Object msg) {

        switch ((String)msg) {
            default:
                super.onReceive(msg);
                break;
        }
    }

    @Override protected void onTick() {

        notifyContestStartsInOneHour();
        notifyWinners();
    }



    public void prepareWinnerNotification(User user, Contest contest) {
        Notification nf = new Notification(Topic.CONTEST_WINNER, contest.contestId.toString(), user.userId);
        Model.notifications().insert(nf);
    }

    private void notifyWinners() {
        List<Notification> notificationsPending = Notification.getUnsentNotifications(Topic.CONTEST_WINNER);
        List<User> recipients = new ArrayList<>();

        List<MessageTemplateSend.MandrillMessage.MergeVarBucket> mergeVars = new ArrayList<>();


        for (Notification notification: notificationsPending) {
            User winner = User.findOne(notification.userId);

            if (!(winner.email.endsWith("test.com") || winner.email.endsWith("bototron.com"))) {
                recipients.add(winner);

                ArrayList<Contest> contests = new ArrayList<>();
                Contest contestWon = Contest.findOne(notification.reason);
                contests.add(contestWon);

                mergeVars.add(NotificationActor.prepareMergeVarBucket(winner, contests));
            } else {
                notification.updateAsSent();
            }
        }

        if (recipients.size()>0) {
            boolean sent = MessageTemplateSend.send(recipients, Topic.CONTEST_WINNER.toString(), "En Epic Eleven has ganado", mergeVars);
            if (sent) {
                for (Notification notification : notificationsPending) {
                    notification.updateAsSent();
                }
            }
        }

    }

    private void notifyContestStartsInOneHour() {
        List<Contest> nextContests = Contest.findAllStartingIn(1);

        List<User> recipients = new ArrayList<>();
        List<MessageTemplateSend.MandrillMessage.MergeVarBucket> mergeVars = new ArrayList<>();

        List<Notification> notifications = new ArrayList<>();

        for (Map.Entry<ObjectId, ArrayList<Contest>> entry : getUsersForContests(nextContests).entrySet()) {
            ObjectId userId = entry.getKey();

            Notification lastNotification = Notification.getLastNotification(Topic.CONTEST_NEXT_HOUR, userId);
            DateTime nextNotifiableDate = (lastNotification!= null)? new DateTime(lastNotification.createdAt).plusHours(1) : new DateTime(0L);

            ArrayList<Contest> notifiableContests = new ArrayList<>();
            for (Contest contest : entry.getValue()) {
                if (nextNotifiableDate.isBefore(new DateTime(contest.startDate))) {
                    notifiableContests.add(contest);
                }
            }

            if (notifiableContests.size() > 0) {
                User currentUserToNotify = User.findOne(userId);
                recipients.add(currentUserToNotify);
                mergeVars.add(prepareMergeVarBucket(currentUserToNotify, notifiableContests));
                notifications.add(new Notification(Topic.CONTEST_NEXT_HOUR, null, userId));

                Logger.debug("Sending notification {} to user {}, {} contests", Topic.CONTEST_NEXT_HOUR.toString(), currentUserToNotify.email, notifiableContests.size());
            }
        }

        if (recipients.size() > 0) {
            boolean sent = MessageTemplateSend.send(recipients, _contestStartingTemplateName, "En Epic Eleven tienes concursos por comenzar", mergeVars);

            if (sent) {
                for (Notification notification : notifications) {
                    notification.insertAsSent();
                }

                Logger.debug("Notification {} sent to {} users", Topic.CONTEST_NEXT_HOUR.toString(), recipients.size());
            }
        }
    }
    
    public static MessageTemplateSend.MandrillMessage.MergeVarBucket prepareMergeVarBucket(User user, ArrayList<Contest> thisUsersContests) {
        MessageTemplateSend.MergeVar name = new MessageTemplateSend.MergeVar();
        MessageTemplateSend.MergeVar contests = new MessageTemplateSend.MergeVar();

        name.name = "NICKNAME";
        name.content = user.nickName;

        contests.name = "TORNEOS";
        contests.content =  views.html.email_contest_start_template.render(thisUsersContests).toString();

        MessageTemplateSend.MandrillMessage.MergeVarBucket mvb = new MessageTemplateSend.MandrillMessage.MergeVarBucket();
        mvb.rcpt = user.email;
        mvb.vars = new ArrayList<>();

        mvb.vars.add(name);
        mvb.vars.add(contests);

        return mvb;
    }


    private Map<ObjectId, ArrayList<Contest>> getUsersForContests(List<Contest> nextContests) {
        Map<ObjectId, ArrayList<Contest>> usersContestsMap = new HashMap<>();

        for (Contest contest : nextContests) {
            for (ContestEntry contestEntry : contest.contestEntries) {

                ArrayList<Contest> thisUsersContests = usersContestsMap.containsKey(contestEntry.userId)?
                    usersContestsMap.get(contestEntry.userId) : new ArrayList<Contest>();

                thisUsersContests.add(contest);

                usersContestsMap.put(contestEntry.userId, thisUsersContests);
            }

        }
        return usersContestsMap;
    }

    private final String _contestStartingTemplateName = "CONTEST_NEXT_HOUR";
}
