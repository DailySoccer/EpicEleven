package actors;

import akka.actor.UntypedActor;
import model.GlobalDate;
import model.TemplateContest;
import play.Logger;
import scala.concurrent.duration.Duration;
import java.util.concurrent.TimeUnit;

public class GivePrizesActor extends UntypedActor {
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
        Logger.info("Give Prizes: {}", GlobalDate.getCurrentDateString());

        for (TemplateContest templateContest : TemplateContest.findHistoryNotClosed()) {
            templateContest.givePrizes();

            // throw new RuntimeException();
            templateContest.setClosed();
        }
    }
}
