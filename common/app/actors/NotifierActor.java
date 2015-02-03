package actors;

import akka.actor.UntypedActor;
import model.Contest;
import model.ContestEntry;
import model.GlobalDate;
import model.User;
import model.notification.MessageTemplateSend;
import model.notification.Notification.Topic;
import play.Logger;
import scala.concurrent.duration.Duration;

import java.util.*;
import java.util.concurrent.TimeUnit;


public class NotifierActor extends UntypedActor {

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
        Logger.debug("NotifierActor: {}", GlobalDate.getCurrentDateString());

        notifyContestStartsInOneHour();
        // All the rest of things to notify in each tick
    }


    private void notifyContestStartsInOneHour() {
        List<Contest> nextContests = Contest.findAllStartingIn(1);

        Map<User, ArrayList<Contest>> nextUsersContests = getUsersForContests(nextContests);

        List<MessageTemplateSend.MandrillMessage.MergeVarBucket> mergeVars = new ArrayList<>();
        Map<User, String> reasonsPerEmail = new HashMap<>();

        for (User user: nextUsersContests.keySet()) {

            ArrayList<Contest> thisUsersContests = nextUsersContests.get(user);

            Collections.sort(thisUsersContests, new Comparator<Contest>() {
               @Override
                public int compare(Contest contest1, Contest contest2) {
                   return contest1.contestId.compareTo(contest2.contestId);
               }
            });

            String sortedContestsString = "";
            for (Contest contest : thisUsersContests) {
                sortedContestsString = sortedContestsString.concat(contest.contestId.toString());
            }
            reasonsPerEmail.put(user, sortedContestsString);


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

            mergeVars.add(mvb);
        }

        MessageTemplateSend.notifyIfNotYetNotified("torneoporempezar", Topic.CONTEST_NEXT_HOUR, "En Epic Eleven tienes concursos por comenzar", mergeVars, reasonsPerEmail);

    }


    private Map<User, ArrayList<Contest>> getUsersForContests(List<Contest> nextContests) {
        Map<User, ArrayList<Contest>> usersContestsMap = new HashMap<>();

        for (Contest contest : nextContests) {
            for (ContestEntry contestEntry : contest.contestEntries) {
                User user = User.findOne(contestEntry.userId);

                ArrayList<Contest> thisUsersContests = usersContestsMap.containsKey(user)?
                    usersContestsMap.get(user) : new ArrayList<Contest>();

                thisUsersContests.add(contest);

                usersContestsMap.put(user, thisUsersContests);
            }

        }
        return usersContestsMap;
    }
}
