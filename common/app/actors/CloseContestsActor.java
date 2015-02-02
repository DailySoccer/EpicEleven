package actors;

import akka.actor.UntypedActor;
import model.Contest;
import model.GlobalDate;
import play.Logger;
import scala.concurrent.duration.Duration;
import java.util.concurrent.TimeUnit;

public class CloseContestsActor extends UntypedActor {
    public void onReceive(Object msg) {

        switch ((String)msg) {

            case "Tick":
                onTick();
                getContext().system().scheduler().scheduleOnce(Duration.create(60, TimeUnit.MINUTES), getSelf(),
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
        Logger.debug("CloseContestsActor: {}", GlobalDate.getCurrentDateString());

        for (Contest contest : Contest.findAllHistoryNotClosed()) {
            contest.closeContest();
        }
    }
}
