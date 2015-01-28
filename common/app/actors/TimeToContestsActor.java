package actors;

import akka.actor.UntypedActor;
import model.Contest;
import model.ContestEntry;
import model.GlobalDate;
import model.User;
import model.emailing.MessageSend;
import play.Logger;
import play.twirl.api.Html;
import scala.concurrent.duration.Duration;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;


public class TimeToContestsActor extends UntypedActor {

    public void onReceive(Object msg) {

        switch ((String)msg) {

            case "Tick":
                onTick();
                getContext().system().scheduler().scheduleOnce(Duration.create(1, TimeUnit.MINUTES), getSelf(),
                                                               "Tick", getContext().dispatcher(), null);
                break;

            // En el caso del SimulatorTick no tenemos que reeschedulear el mensaje porque es el Simulator el que se
            // encarga de drivearnos.
            case "SimulatorTick":
                onTick();
                break;

            default:
                unhandled(msg);
                break;
        }
    }

    private void onTick() {
        Logger.debug("TimeToContestsActor: {}", GlobalDate.getCurrentDateString());

        List<Contest> nextContests = Contest.findAllStartingInXhours(1);

        Map<User, ArrayList<Contest>> nextUsersContests = getUsersForContests(nextContests);

        for (User user: nextUsersContests.keySet()) {
            Html html = views.html.remaining.render(user.nickName, nextUsersContests.get(user), nextUsersContests.get(user).size() );
            Logger.debug(html.toString());
            MessageSend.send(user.nickName, user.email, nextUsersContests.get(user).size()+" concursos por empezar!", html.toString());
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
