package actors;

import akka.actor.Cancellable;
import akka.actor.UntypedActor;
import model.GlobalDate;
import play.Logger;
import play.Play;
import scala.concurrent.duration.Duration;
import scala.concurrent.duration.FiniteDuration;
import java.util.concurrent.TimeUnit;


public abstract class TickableActor extends UntypedActor {

    abstract protected void onTick();
    protected          void onSimulatorTick() { onTick(); }


    @Override public void preStart() {
        Logger.debug("TickableActor::{} preStart", getActorName());

        String actorName = getLowerCaseActorName();

        Long millisecs = Play.application().configuration().getMilliseconds(actorName + ".tickInterval");
        if (millisecs == null) {
            millisecs = 60000L;
            Logger.warn("{} no tiene configurado su tickInterval!", actorName);
        }
        _duration = Duration.create(millisecs, TimeUnit.MILLISECONDS);

        Boolean autoStart = Play.application().configuration().getBoolean(actorName + ".autoStart");
        if (autoStart != null && autoStart) {
            self().tell("Tick", getSelf()); // Primer tick inmediato
        }
    }

    @Override public void postStop() {
        Logger.debug("TickableActor::{} postStop", getActorName());

        if (_tickCancellable != null) {
            _tickCancellable.cancel();
            _tickCancellable = null;
        }
    }

    @Override public void onReceive(Object msg) {
        switch ((String)msg) {
            case "Tick":
                Logger.debug("{} Tick @ {}", getActorName(), GlobalDate.getCurrentDateString());

                onTick();
                reescheduleTick();
                break;

            case "SimulatorTick":
                Logger.debug("{} SimulatorTick @ {}", getActorName(), GlobalDate.getCurrentDateString());

                onSimulatorTick();
                break;
            default:
                unhandled(msg);
                break;
        }
    }

    private void reescheduleTick() {
        _tickCancellable = getContext().system().scheduler().scheduleOnce(_duration, getSelf(), "Tick", getContext().dispatcher(), null);
    }

    private String getActorName() {
        return self().path().name();
    }

    private String getLowerCaseActorName() {
        String actorName = getActorName();
        return actorName.substring(0, 1).toLowerCase() + actorName.substring(1);
    }

    FiniteDuration _duration;
    Cancellable _tickCancellable;
}
