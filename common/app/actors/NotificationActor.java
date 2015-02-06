package actors;

import akka.actor.UntypedActor;
import model.Contest;
import model.ContestEntry;
import model.GlobalDate;
import model.User;
import model.notification.MessageTemplateSend;
import model.notification.Notification;
import model.notification.Notification.Topic;
import org.bson.types.ObjectId;
import org.joda.time.DateTime;
import play.Logger;
import scala.concurrent.duration.Duration;

import java.util.*;
import java.util.concurrent.TimeUnit;


public class NotificationActor extends UntypedActor {

    public void onReceive(Object msg) {

        switch ((String)msg) {

            case "Tick":
                onTick();
                getContext().system().scheduler().scheduleOnce(Duration.create(1, TimeUnit.MINUTES), getSelf(),
                                                               "Tick", getContext().dispatcher(), null);
                break;

            case "SimulatorTick":
                onTick();
                break;

            default:
                unhandled(msg);
                break;
        }
    }

    private void onTick() {
        Logger.debug("NotificationActor: {}", GlobalDate.getCurrentDateString());

        notifyContestStartsInOneHour();
        // All the rest of things to notify in each tick
    }


    private void notifyContestStartsInOneHour() {
        List<Contest> nextContests = Contest.findAllStartingIn(1);

        Map<ObjectId, ArrayList<Contest>> nextUsersContests = getUsersForContests(nextContests);
        Map<String, String> recipients = new HashMap<>();

        List<MessageTemplateSend.MandrillMessage.MergeVarBucket> mergeVars = new ArrayList<>();

        List<Notification> notifications = new ArrayList<>();

        int i = 0;
        for (ObjectId userId: nextUsersContests.keySet()) {
            if (++i>2) {
                break;
            }

            ArrayList<Contest> thisUsersContests = nextUsersContests.get(userId);
            ArrayList<Contest> notifiableContests = new ArrayList<>();

            Notification lastNotification = Notification.getLastNotification(Topic.CONTEST_NEXT_HOUR, userId);

            DateTime nextNotifiableDate =  (lastNotification!= null)? new DateTime(lastNotification.createdAt).plusHours(1) : new DateTime(0L);

            for (Contest contest : thisUsersContests) {
                if (nextNotifiableDate.isBefore(new DateTime(contest.startDate))) {
                    notifiableContests.add(contest);
                }
            }


            if (notifiableContests.size() > 0) {
                User currentUserToNotify = User.findOne(userId);
                recipients.put(currentUserToNotify.email, currentUserToNotify.nickName);
                mergeVars.add(prepareMergeVarBucket(currentUserToNotify, notifiableContests));
                notifications.add(new Notification(Topic.CONTEST_NEXT_HOUR, null, userId));
            }
        }

        if (recipients.size() > 0 && MessageTemplateSend.send(recipients, _contestStartingTemplateName, "En Epic Eleven tienes concursos por comenzar", mergeVars)) {
            for (Notification notification: notifications) {
                notification.markSent();
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

    private final String _contestStartingTemplateName = "torneoporempezar";
}
