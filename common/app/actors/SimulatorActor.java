package actors;

import akka.actor.ActorRef;
import akka.actor.Cancellable;
import akka.actor.UntypedActor;
import model.GlobalDate;
import model.Model;
import model.opta.OptaXmlUtils;
import org.joda.time.DateTime;
import play.Logger;
import play.libs.Akka;
import scala.concurrent.duration.Duration;

import java.util.Date;
import java.util.concurrent.TimeUnit;

public class SimulatorActor extends UntypedActor {

    static public final int MAX_SPEED = -1;

    @Override public void onReceive(Object msg) {

        switch ((String)msg) {
            case "InitShutdown":
                if (_state == null) {
                    init();
                }
                else {
                    shutdown();
                }
                break;
            case "Tick":
                onTick();
                break;

            default:
                unhandled(msg);
                break;
        }
    }

    private void shutdown() {
        cancelTicking();
        _state = null;
        GlobalDate.setFakeDate(null);
        Model.getDailySoccerActors().tellToActor("OptaProcessorActor", "SimulatorShutdown");
    }

    private void init() {

        Date lastProcessedDate = OptaProcessorActor.getLastProcessedDate(); // TODO: Llamada tellToActorAwaitResult

        _state = Model.simulator().findOne().as(SimulatorState.class);

        if (_state == null) {
            _state = new SimulatorState();

            _state.simulationDate = lastProcessedDate;

            // Cuando todavia nadie ha procesado ningun documento, nos ponemos en el primero que haya
            if (_state.simulationDate.equals(new Date(0L))) {
                _state.simulationDate = OptaXmlUtils.getFirstDate();
            }
        }
        else {
            // Reseteamos la fecha de simulacion en caso de que el proceso haya avanzado por su cuenta
            if (_state.simulationDate.before(lastProcessedDate)) {
                _state.simulationDate = lastProcessedDate;
            }

            // Tenemos registrada una fecha antigua de pausa?
            if (_state.pauseDate != null && !_state.simulationDate.before(_state.pauseDate)) {
                _state.pauseDate = null;
            }
        }

        // Siempre comenzamos pausados
        // _paused = true;
        startTicking(); // Temp

        // Nos ponemos en la fecha y grabamos el estado
        updateDate(_state.simulationDate);

        // Ponemos al OptaProcessorActor en el modo que nos interesa
        Model.getDailySoccerActors().tellToActor("OptaProcessorActor", "SimulatorInit");
    }

    private void onTick() {
        Logger.debug("SimulatorActor: {}", GlobalDate.getCurrentDate());

        // Es posible que nos encolen una parada mientras procesabamos el Tick (porque a su vez encolamos otro tick)
        if (_state == null) {
            return;
        }

        if (_state.speedFactor != MAX_SPEED) {
            int virtualElapsedTime = TICK_PERIOD * _state.speedFactor;

            updateDate(new DateTime(_state.simulationDate).plusMillis(virtualElapsedTime).toDate());

            reescheduleTick();
        }
        else {
            // Apuntamos la GlobalDate exactamente a la del siguiente documento
            OptaProcessorActor.NextDocMsg nextdocMsg = (OptaProcessorActor.NextDocMsg)Model.getDailySoccerActors()
                                                        .tellToActorAwaitResult("OptaProcessorActor", "GetNextDoc");
            updateDate(nextdocMsg.date);

            // Encolamos un mensaje para ejecucion inmediata!
            getSelf().tell("Tick", getSelf());
        }

        // El orden de entrega de estos mensajes no esta garantizado, como debe de ser. 
        Model.getDailySoccerActors().tellToActor("OptaProcessorActor", "SimulatorTick");
        Model.getDailySoccerActors().tellToActor("InstantiateConstestsActor", "SimulatorTick");
        Model.getDailySoccerActors().tellToActor("CloseContestsActor", "SimulatorTick");
        Model.getDailySoccerActors().tellToActor("TransactionsActor", "SimulatorTick");
    }

    private void updateDate(Date currentDate) {
        _state.simulationDate = currentDate;
        saveStateToDB();

        GlobalDate.setFakeDate(_state.simulationDate);
    }

    private void saveStateToDB() {
        // Unico punto donde grabamos nuestro estado a la DB
        Model.simulator().update("{stateId: #}", _state.stateId).upsert().with(_state);
    }

    private void startTicking() {
        if (_tickCancellable != null) {
            throw new RuntimeException("WTF 8722");
        }

        reescheduleTick();
    }

    private void cancelTicking() {
        if (_tickCancellable != null) {
            _tickCancellable.cancel();
            _tickCancellable = null;
        }
    }

    private void reescheduleTick() {
        _tickCancellable = getContext().system().scheduler().scheduleOnce(Duration.create(TICK_PERIOD, TimeUnit.MILLISECONDS), getSelf(), "Tick",
                                                                          getContext().dispatcher(), null);
    }

    SimulatorState _state;
    Cancellable _tickCancellable;

    static final int TICK_PERIOD = 1000;   // Milliseconds

    static private class SimulatorState {
        public String  stateId = "--SimulatorState--";
        public Date    pauseDate;
        public Date    simulationDate;
        public int     speedFactor = MAX_SPEED;

        public SimulatorState() {}
    }
}
