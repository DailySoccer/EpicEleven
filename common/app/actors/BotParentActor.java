package actors;


import akka.actor.ActorRef;
import akka.actor.Cancellable;
import akka.actor.Props;
import akka.actor.UntypedActor;
import play.Logger;
import scala.concurrent.duration.Duration;
import scala.concurrent.duration.FiniteDuration;

import java.util.concurrent.TimeUnit;

public class BotParentActor extends UntypedActor {

    @Override public void postStop() {
        // Para evitar que nos lleguen cartas de muertos
        cancelTicking();
    }

    @Override
    public void onReceive(Object msg) {
        switch ((String) msg) {
            case "StartChildren":
                if (!_childrenStarted) {
                    Logger.debug("BotParentActor arrancando bots hijos");

                    for (int c = 0; c < _NUM_BOTS; ++c) {
                        getContext().actorOf(Props.create(BotActor.class, c, BotActor.Personality.PRODUCTION), String.format("BotActor%d", c));
                    }

                    _childrenStarted = true;
                    _currentActorIdTick = 0;
                    startTicking();
                }
                else {
                    Logger.error("WTF 1567 Recibido StartChildren a destiempo");
                }

                sender().tell(_childrenStarted, getSelf());

                break;

            case "StopChildren":
                if (_childrenStarted) {
                    Logger.debug("BotParentActor parando bots hijos");

                    // Since stopping an actor is asynchronous, you cannot immediately reuse the name of the child you just stopped;
                    // this will result in an InvalidActorNameException. Instead, watch the terminating actor and create its
                    // replacement in response to the Terminated message which will eventually arrive.
                    // gracefulStop is useful if you need to wait for termination.
                    // http://doc.akka.io/docs/akka/2.3.8/java/untyped-actors.html
                    for (ActorRef child : getContext().getChildren()) {
                        getContext().stop(child);
                    }

                    _childrenStarted = false;
                    cancelTicking();
                }
                else {
                    Logger.error("WTF 1560 Recibido StopChildren a destiempo");
                }

                sender().tell(_childrenStarted, getSelf());

                break;

            case "GetChildrenStarted":
                sender().tell(_childrenStarted, getSelf());
                break;

            case "NormalTick":
                ActorRef child = getContext().getChild(String.format("BotActor%d", _currentActorIdTick));

                // Es posible que el actor este muerto (temporalmente en caso de excepcion procesando un mensaje o permanentemente
                // si no pudo inicializar). Nos lo saltamos
                if (child != null) {
                    child.tell("Tick", getSelf());
                }

                _currentActorIdTick++;
                if (_currentActorIdTick >= _NUM_BOTS) {
                    _currentActorIdTick = 0;
                }

                reescheduleTick(Duration.create(1, TimeUnit.SECONDS));
                break;

            case "AggressiveTick":
                for (ActorRef actorRef : getContext().getChildren()) {
                    actorRef.tell("Tick", getSelf());
                }

                reescheduleTick(Duration.create(5, TimeUnit.SECONDS));
                break;

            case "NextPersonality":
                for (ActorRef actorRef : getContext().getChildren()) {
                    actorRef.tell("NextPersonality", getSelf());
                }

                reescheduleCyclePersonalities(Duration.create(60, TimeUnit.SECONDS));
                break;

            default:
                unhandled(msg);
                break;
        }
    }

    private void startTicking() {
        getSelf().tell(_currentTickMode.name(), getSelf());
        reescheduleCyclePersonalities(Duration.create(60, TimeUnit.SECONDS));
    }

    private void cancelTicking() {
        if (_tickCancellable != null) {
            _tickCancellable.cancel();
            _tickCancellable = null;
        }
        if (_cycleCancellable != null) {
            _cycleCancellable.cancel();
            _cycleCancellable = null;
        }
    }

    private void reescheduleCyclePersonalities(FiniteDuration duration) {
        if (_cyclePersonalities) {
            _cycleCancellable = getContext().system().scheduler().scheduleOnce(duration, getSelf(), "NextPersonality",
                                                                               getContext().dispatcher(), null);
        }
    }

    private void reescheduleTick(FiniteDuration duration) {
        _tickCancellable = getContext().system().scheduler().scheduleOnce(duration, getSelf(), _currentTickMode.name(),
                                                                          getContext().dispatcher(), null);
    }

    static final int _NUM_BOTS = 30;

    enum TickingMode {
        NormalTick,     // En minisculas pq los vamos a usar directamente asString
        AggressiveTick
    };


    boolean _childrenStarted = false;

    TickingMode _currentTickMode = TickingMode.NormalTick;
    Cancellable _tickCancellable;
    int _currentActorIdTick;

    boolean _cyclePersonalities = true;
    Cancellable _cycleCancellable;
}
