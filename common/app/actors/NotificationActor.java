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
        Map<ObjectId, String> reasonsPerEmail = new HashMap<>();

        for (ObjectId userId: nextUsersContests.keySet()) {

            ArrayList<Contest> thisUsersContests = nextUsersContests.get(userId);
            ArrayList<Contest> notifiableContests = new ArrayList<>();


            Notification lastNotification = Notification.getNotification(Topic.CONTEST_NEXT_HOUR, userId);

            Date lastNotified =  (lastNotification!= null)? GlobalDate.parseDate(lastNotification.reason, "UTC") : new Date(0L);
            Date lastContestDate = lastNotified;


            for (Contest contest : thisUsersContests) {
                if (contest.startDate.after(lastNotified)) {
                    notifiableContests.add(contest);
                    if (contest.startDate.after(lastContestDate)) {
                        lastContestDate = contest.startDate;
                    }
                }
            }


            if (notifiableContests.size() > 0) {
                User currentUserToNotify = User.findOne(userId);
                recipients.put(currentUserToNotify.email, currentUserToNotify.nickName);
                reasonsPerEmail.put(userId, GlobalDate.formatDate(lastContestDate));
                mergeVars.add(prepareMergeVarBucket(currentUserToNotify, notifiableContests));
            }


        }

        MessageTemplateSend.notifyUpdating(_contestStartingTemplateName, Topic.CONTEST_NEXT_HOUR, "En Epic Eleven tienes concursos por comenzar", mergeVars, reasonsPerEmail, recipients);

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
