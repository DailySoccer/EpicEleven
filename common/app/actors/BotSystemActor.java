package actors;

import akka.actor.*;
import play.Logger;
import play.Play;
import scala.concurrent.Await;
import scala.concurrent.duration.Duration;
import scala.concurrent.duration.FiniteDuration;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class BotSystemActor extends UntypedActor {

    public enum ChildrenState {
        STARTED,
        PAUSED,
        STOPPED
    }

    @Override public void postStop() {
        // Para evitar que nos lleguen cartas de muertos
        cancelTicking();
        stopActors();
    }

    private void readConfig() {
        _numBots = Play.application().configuration().getInt("botSystem.numBots");
        _tickInterval = Duration.create(Play.application().configuration().getInt("botSystem.tickInterval"), TimeUnit.MILLISECONDS);
        _tickMode = TickingMode.valueOf(Play.application().configuration().getString("botSystem.tickMode"));
        _personality = BotActor.Personality.valueOf(Play.application().configuration().getString("botSystem.personality"));
        _cyclePersonalities = Play.application().configuration().getBoolean("botSystem.cyclePersonalities");
        _cyclePersonalitiesInterval = Duration.create(Play.application().configuration().getInt("botSystem.cyclePersonalitiesInterval"), TimeUnit.MILLISECONDS);
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

    private void startChildren() {
        Logger.debug("BotSystemActor arrancando {} bots hijos", _numBots);
        readConfig();
        createActors();
        startTicking();
        Logger.debug("BotSystemActor {} hijos arrancados", _numBots);
    }

    private void stopChildren() {
        Logger.debug("BotSystemActor parando {} bots hijos", _numBots);
        cancelTicking();
        stopActors();
        Logger.debug("BotSystemActor {} hijos parados", _numBots);
    }

    private void onReceive(String msg) {

        switch (msg) {
            case "StartStop":
                if (!_childrenStarted) {
                    startChildren();
                }
                else {
                    stopChildren();
                }

                sender().tell(getCurrentChildrenState(), getSelf());
                break;

            case "PauseResume":
                if (_childrenStarted) {
                    if (_tickCancellable != null) {
                        Logger.debug("BotSystemActor pausando hijos");
                        cancelTicking();
                        Logger.debug("BotSystemActor hijos pausados");
                    }
                    else {
                        Logger.debug("BotSystemActor resumiendo hijos");
                        startTicking();
                        Logger.debug("BotSystemActor hijos resumidos");
                    }
                }
                else {
                    Logger.error("WTF 1561 Recibido PauseResume a destiempo");
                }

                sender().tell(getCurrentChildrenState(), getSelf());
                break;

            case "GetChildrenState":
                sender().tell(getCurrentChildrenState(), getSelf());
                break;

            case "Stampede":
                Logger.debug("BotSystemActor Stampede!");
                for (ActorRef actorRef : getContext().getChildren()) {
                    actorRef.tell("LeaveAllContests", getSelf());
                    cancelTicking();
                }
                Logger.debug("BotSystemActor Stampede end");

                sender().tell(getCurrentChildrenState(), getSelf());
                break;

            case "NORMAL_TICK":
                ActorRef child = getContext().getChild(String.format("BotActor%d", _currentActorIdTick));

                // Es posible que el actor este muerto (temporalmente en caso de excepcion procesando un mensaje o permanentemente
                // si no pudo inicializar). Nos lo saltamos
                if (child != null) {
                    child.tell(new BotActor.BotMsg("Tick", null, _averageEnteredContests), getSelf());
                }

                _currentActorIdTick++;
                if (_currentActorIdTick >= _numBots) {
                    _currentActorIdTick = 0;
                }

                reescheduleTick();
                break;

            case "AGGRESSIVE_TICK":
                for (ActorRef actorRef : getContext().getChildren()) {
                    actorRef.tell(new BotActor.BotMsg("Tick", null, _averageEnteredContests), getSelf());
                }

                reescheduleTick();
                break;

            case "NextPersonality":
                for (ActorRef actorRef : getContext().getChildren()) {
                    actorRef.tell("NextPersonality", getSelf());
                }

                reescheduleCyclePersonalities();
                break;

            default:
                unhandled(msg);
                break;
        }
    }

    private ChildrenState getCurrentChildrenState() {
        ChildrenState currentState = ChildrenState.STOPPED;

        if (_childrenStarted) {
            currentState = (_tickCancellable == null)? ChildrenState.PAUSED : ChildrenState.STARTED;
        }

        return currentState;
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


    private void createActors() {
        for (int c = 0; c < _numBots; ++c) {
            getContext().actorOf(Props.create(BotActor.class, c, _personality), String.format("BotActor%d", c));
        }

        _childrenStarted = true;
        _currentActorIdTick = 0;

        _botEnteredContests = new HashMap<>();
        _averageEnteredContests = 0;
    }


    private void stopActors() {
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
    }

    private void startTicking() {
        // Primer tick inmediato...
        getSelf().tell(_tickMode.name(), getSelf());

        // ...en la personalidad que hubiera configurada
        reescheduleCyclePersonalities();
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

    private void reescheduleCyclePersonalities() {
        if (_cyclePersonalities) {
            _cycleCancellable = getContext().system().scheduler().scheduleOnce(_cyclePersonalitiesInterval, getSelf(), "NextPersonality",
                                                                               getContext().dispatcher(), null);
        }
    }

    private void reescheduleTick() {
        _tickCancellable = getContext().system().scheduler().scheduleOnce(_tickInterval, getSelf(), _tickMode.name(),
                                                                          getContext().dispatcher(), null);
    }

    enum TickingMode {
        NORMAL_TICK,
        AGGRESSIVE_TICK
    }

    boolean _childrenStarted = false;
    int _numBots = 30;

    TickingMode _tickMode = TickingMode.NORMAL_TICK;
    FiniteDuration _tickInterval = Duration.create(1, TimeUnit.SECONDS);
    Cancellable _tickCancellable;
    int _currentActorIdTick;

    BotActor.Personality _personality = BotActor.Personality.PRODUCTION;
    boolean _cyclePersonalities = false;
    FiniteDuration _cyclePersonalitiesInterval = Duration.create(60, TimeUnit.SECONDS);
    Cancellable _cycleCancellable;

    HashMap<String, Integer> _botEnteredContests;
    float _averageEnteredContests;
}
