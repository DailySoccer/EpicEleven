package actors;


import akka.actor.*;
import akka.pattern.AskTimeoutException;
import play.Logger;
import scala.concurrent.Await;
import scala.concurrent.duration.Duration;
import scala.concurrent.duration.FiniteDuration;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class BotParentActor extends UntypedActor {

    @Override public void postStop() {
        // Para evitar que nos lleguen cartas de muertos
        cancelTicking();
    }

    @Override
    public void onReceive(Object msg) {

        if (msg instanceof BotActor.BotMsg) {
            onReceive((BotActor.BotMsg)msg);
        }
        else if (msg instanceof String) {
            onReceive((String)msg);
        }
        else {
            unhandled(msg);
        }
    }

    private void onReceive(String msg) {

        switch (msg) {
            case "StartChildren":
                if (!_childrenStarted) {
                    Logger.debug("BotParentActor arrancando {} bots hijos", _NUM_BOTS);
                    startTicking();
                }
                else {
                    Logger.error("WTF 1567 Recibido StartChildren a destiempo");
                }
                break;

            case "StopChildren":
                if (_childrenStarted) {
                    Logger.debug("BotParentActor parando {} bots hijos", _NUM_BOTS);
                    cancelTicking();
                    Logger.debug("BotParentActor {} hijos parados", _NUM_BOTS);
                }
                else {
                    Logger.error("WTF 1560 Recibido StopChildren a destiempo");
                }

                break;

            case "GetChildrenStarted":
                sender().tell(_childrenStarted, getSelf());
                break;

            case "NormalTick":
                ActorRef child = getContext().getChild(String.format("BotActor%d", _currentActorIdTick));

                // Es posible que el actor este muerto (temporalmente en caso de excepcion procesando un mensaje o permanentemente
                // si no pudo inicializar). Nos lo saltamos
                if (child != null) {
                    child.tell(new BotActor.BotMsg("Tick", null, _averageEnteredContests), getSelf());
                }

                _currentActorIdTick++;
                if (_currentActorIdTick >= _NUM_BOTS) {
                    _currentActorIdTick = 0;
                }

                reescheduleTick(Duration.create(1, TimeUnit.SECONDS));
                break;

            case "AggressiveTick":
                for (ActorRef actorRef : getContext().getChildren()) {
                    actorRef.tell(new BotActor.BotMsg("Tick", null, _averageEnteredContests), getSelf());
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

    private void onReceive(BotActor.BotMsg msg) {

        switch (msg.msg) {
            case "CurrentEnteredContests":
                _botEnteredContests.put(msg.userId, (Integer) (msg.param));
                recalcAverageEnteredContests();
                break;

            default:
                unhandled(msg);
                break;
        }
    }

    private void recalcAverageEnteredContests() {
        int sum = 0;
        for (Map.Entry<String, Integer> entry : _botEnteredContests.entrySet()) {
            sum += entry.getValue();
        }
        _averageEnteredContests = (float)sum / (float) _botEnteredContests.size();
    }

    private void startTicking() {

        for (int c = 0; c < _NUM_BOTS; ++c) {
            getContext().actorOf(Props.create(BotActor.class, c, _startingPersonality), String.format("BotActor%d", c));
        }

        _childrenStarted = true;
        _currentActorIdTick = 0;

        _botEnteredContests = new HashMap<>();
        _averageEnteredContests = 0;

        getSelf().tell(_currentTickMode.name(), getSelf());
        reescheduleCyclePersonalities(Duration.create(60, TimeUnit.SECONDS));
    }

    private void cancelTicking() {

        // Since stopping an actor is asynchronous, you cannot immediately reuse the name of the child you just stopped;
        // this will result in an InvalidActorNameException. Instead, watch the terminating actor and create its
        // replacement in response to the Terminated message which will eventually arrive.
        // gracefulStop is useful if you need to wait for termination.
        // http://doc.akka.io/docs/akka/2.3.8/java/untyped-actors.html
        for (ActorRef actorRef : getContext().getChildren()) {
            try {
                scala.concurrent.Future<Boolean> stopped = akka.pattern.Patterns.gracefulStop(actorRef, Duration.create(5, TimeUnit.SECONDS), PoisonPill.getInstance());
                Await.result(stopped, Duration.create(6, TimeUnit.SECONDS));
            } catch (Exception e) {
                Logger.error("WTF 2211 The actor wasn't stopped within 5 seconds");
            }
        }

        _childrenStarted = false;

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

    BotActor.Personality _startingPersonality = BotActor.Personality.PRODUCTION;
    boolean _cyclePersonalities = false;
    Cancellable _cycleCancellable;

    HashMap<String, Integer> _botEnteredContests;
    float _averageEnteredContests;
}
