package actors;

import akka.actor.UntypedActor;
import model.GlobalDate;
import model.TemplateContest;
import play.Logger;
import scala.concurrent.duration.Duration;

import java.util.List;
import java.util.concurrent.TimeUnit;


public class InstantiateContestsActor extends UntypedActor {

    public void onReceive(Object msg) {

        switch ((String)msg) {

            case "Start":
            case "Tick":
                onTick();
                getContext().system().scheduler().scheduleOnce(Duration.create(1, TimeUnit.SECONDS), getSelf(),
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
        Logger.info("InstantiateContestsActor: {}", GlobalDate.getCurrentDateString());

        List<TemplateContest> templateContestsOff = TemplateContest.findAllByActivationAt(GlobalDate.getCurrentDate());

        for (TemplateContest templateContest : templateContestsOff) {

            // El TemplateContest instanciara sus Contests y MatchEvents asociados
            templateContest.instantiate();
        }
    }
}
