package actors;

import akka.actor.UntypedActor;
import model.Contest;
import model.ContestEntry;
import model.GlobalDate;
import model.User;
import model.notification.MessageSend;
import model.notification.Notification.Topic;
import play.Logger;
import scala.concurrent.duration.Duration;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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

        for (User user: nextUsersContests.keySet()) {
            MessageSend.notifyIfNotYetNotified(user, Topic.CONTEST_NEXT_HOUR,
                    nextUsersContests.get(user).size() + " concursos van a comenzar!",
                    views.html.email_contest_start.render(user.nickName, nextUsersContests.get(user), nextUsersContests.get(user).size()).toString());
        }
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
