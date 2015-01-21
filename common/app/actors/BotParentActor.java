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
        cancelTick();
    }

    @Override
    public void onReceive(Object msg) {
        switch ((String) msg) {
            case "StartChildren":
                if (!_childrenStarted) {
                    Logger.debug("BotParentActor arrancando bots hijos");

                    for (int c = 0; c < _NUM_BOTS; ++c) {
                        getContext().actorOf(Props.create(BotActor.class, c), String.format("BotActor%d", c));
                    }

                    _childrenStarted = true;
                    _currentActorIdTick = 0;
                    getSelf().tell("Tick", getSelf());
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
                    cancelTick();
                }
                else {
                    Logger.error("WTF 1560 Recibido StopChildren a destiempo");
                }

                sender().tell(_childrenStarted, getSelf());

                break;

            case "GetChildrenStarted":
                sender().tell(_childrenStarted, getSelf());
                break;

            case "Tick":
                ActorRef child = getContext().getChild(String.format("BotActor%d", _currentActorIdTick));

                // Es posible que el actor este muerto temporalmente, nos lo saltamos
                if (child != null) {
                    child.tell("Tick", getSelf());

                    _currentActorIdTick++;
                    if (_currentActorIdTick >= _NUM_BOTS) {
                        _currentActorIdTick = 0;
                    }
                }

                reescheduleTick(Duration.create(1, TimeUnit.SECONDS));
                break;
        }
    }

    private void cancelTick() {
        if (_tickCancellable != null) {
            _tickCancellable.cancel();
            _tickCancellable = null;
        }
    }


    private void reescheduleTick(FiniteDuration duration) {
        _tickCancellable = getContext().system().scheduler().scheduleOnce(duration, getSelf(), "Tick", getContext().dispatcher(), null);
    }

    static final int _NUM_BOTS = 30;

    Cancellable _tickCancellable;
    int _currentActorIdTick;
    boolean _childrenStarted = false;
}
