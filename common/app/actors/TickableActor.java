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
            startTicking();
        }
        else {
            _isTicking = false;
        }

        _immediateTicking = false;
    }

    @Override public void postStop() {
        Logger.debug("TickableActor::{} postStop", getActorName());

        stopTicking();
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

            case "GetIsTicking":
                sender().tell(_isTicking, self());
                break;

            case "StartTicking":
                if (!_isTicking) {
                    startTicking();
                }
                else {
                    Logger.debug("WTF 7711 TickableActor.StartTicking estado incorrecto");
                }
                break;

            case "StopTicking":
                if (_isTicking) {
                    stopTicking();
                }
                else {
                    Logger.debug("WTF 7211 TickableActor.StartTicking estado incorrecto");
                }
                break;

            case "StartStopTicking":
                if (!_isTicking) {
                    startTicking();
                }
                else {
                    stopTicking();
                }
                sender().tell(_isTicking, self());
                break;

            default:
                unhandled(msg);
                break;
        }
    }

    private void startTicking() {
        self().tell("Tick", getSelf());
        _isTicking = true;

        Logger.debug("{} started ticking", getActorName());
    }

    private void stopTicking() {
        if (_tickCancellable != null) {
            _tickCancellable.cancel();
            _tickCancellable = null;
            _isTicking = false;
        }

        Logger.debug("{} stopped ticking", getActorName());
    }

    private void reescheduleTick() {
        if (_immediateTicking) {
            _tickCancellable = null;
            self().tell("Tick", getSelf());
        }
        else {
            _tickCancellable = getContext().system().scheduler().scheduleOnce(_duration, getSelf(), "Tick", getContext().dispatcher(), null);
        }
    }

    private String getActorName() {
        return self().path().name();
    }

    private String getLowerCaseActorName() {
        String actorName = getActorName();
        return actorName.substring(0, 1).toLowerCase() + actorName.substring(1);
    }

    protected void setImmediateTicking(boolean enabled) {
        _immediateTicking = enabled;
    }

    boolean _immediateTicking = false;
    boolean _isTicking = false;
    FiniteDuration _duration;
    Cancellable _tickCancellable;
}
