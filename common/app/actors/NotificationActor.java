package actors;

import model.Contest;
import model.ContestEntry;
import model.User;
import model.notification.MessageTemplateSend;
import model.notification.Notification;
import model.notification.Notification.Topic;
import org.bson.types.ObjectId;
import org.joda.time.DateTime;
import play.Logger;

import java.util.*;


public class NotificationActor extends TickableActor {

    @Override public void onReceive(Object msg) {

        switch ((String)msg) {
            default:
                super.onReceive(msg);
                break;
        }
    }

    @Override protected void onTick() {

        // NOTIFICACION DE COMIENZO DE TORNEO DESACTIVADA
        // notifyContestStartsInOneHour();

        notifyWinners();

    }


    private void notifyWinners() {
        Notification lastNotification = Notification.findLastNotification(Topic.CONTEST_WINNER);
        Date lastNotificationDate = (lastNotification!=null)? lastNotification.createdAt : new Date(0L);

        List<Contest> notifiableContests = Contest.findAllClosedAfter(lastNotificationDate);
        Date lastNotifiedContest = lastNotificationDate;

        if (!notifiableContests.isEmpty()) {
            List<User> recipients = new ArrayList<>();
            List<Notification> notificationsPending  = new ArrayList<>();
            List<MessageTemplateSend.MandrillMessage.MergeVarBucket> mergeVars = new ArrayList<>();

            for (Contest contest: notifiableContests) {
                User winner = contest.getWinner();
                if (!(winner.email.endsWith("test.com") || winner.email.endsWith("bototron.com"))) {
                    recipients.add(winner);
                    mergeVars.add(prepareMergeVarBucketWinner(winner, contest));
                    notificationsPending.add(new Notification(Topic.CONTEST_WINNER, contest.contestId.toString(), winner.userId));

                    if (contest.closedAt.after(lastNotifiedContest)) {
                        lastNotifiedContest = contest.closedAt;
                    }

                }
            }


            if (!recipients.isEmpty()) {
                boolean sent = MessageTemplateSend.send(recipients, Topic.CONTEST_WINNER.name(), null, mergeVars);
                if (sent) {
                    for (Notification notification: notificationsPending) {
                        notification.createdAt = lastNotifiedContest;
                        notification.insertAsSent();
                    }
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

            Notification lastNotification = Notification.findLastNotification(Topic.CONTEST_NEXT_HOUR, userId);
            DateTime nextNotifiableDate = (lastNotification!= null)? new DateTime(lastNotification.createdAt).plusHours(1) : new DateTime(0L);

            ArrayList<Contest> notifiableContests = new ArrayList<>();
            for (Contest contest : entry.getValue()) {
                if (nextNotifiableDate.isBefore(new DateTime(contest.startDate))) {
                    notifiableContests.add(contest);
                }
            }

            if (!notifiableContests.isEmpty()) {
                User currentUserToNotify = User.findOne(userId);
                recipients.add(currentUserToNotify);
                mergeVars.add(prepareMergeVarBucket(currentUserToNotify, notifiableContests));
                notifications.add(new Notification(Topic.CONTEST_NEXT_HOUR, null, userId));

                Logger.debug("Sending notification {} to user {}, {} contests", Topic.CONTEST_NEXT_HOUR.toString(), currentUserToNotify.email, notifiableContests.size());
            }
        }

        if (!recipients.isEmpty()) {
            boolean sent = MessageTemplateSend.send(recipients, _contestStartingTemplateName, "En Epic Eleven tienes concursos por comenzar", mergeVars);

            if (sent) {
                notifications.forEach(Notification::insertAsSent);
                Logger.debug("Notification {} sent to {} users", Topic.CONTEST_NEXT_HOUR.toString(), recipients.size());
            }
        }
    }


    private MessageTemplateSend.MandrillMessage.MergeVarBucket prepareMergeVarBucket(User user, ArrayList<Contest> thisUsersContests) {
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



    private MessageTemplateSend.MandrillMessage.MergeVarBucket prepareMergeVarBucketWinner(User user, Contest thisUsersContest) {
        MessageTemplateSend.MergeVar name = new MessageTemplateSend.MergeVar();
        MessageTemplateSend.MergeVar contest = new MessageTemplateSend.MergeVar();
        MessageTemplateSend.MergeVar contestId = new MessageTemplateSend.MergeVar();


        name.name = "NICKNAME";
        name.content = user.nickName;

        contest.name = "TOURNAMENTNAME";
        contest.content =  thisUsersContest.translatedName();

        contestId.name = "CONTESTID";
        contestId.content = thisUsersContest.getId().toString();


        MessageTemplateSend.MandrillMessage.MergeVarBucket mvb = new MessageTemplateSend.MandrillMessage.MergeVarBucket();
        mvb.rcpt = user.email;
        mvb.vars = new ArrayList<>();

        mvb.vars.add(name);
        mvb.vars.add(contest);
        mvb.vars.add(contestId);


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
