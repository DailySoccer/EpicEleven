package actors;

import akka.actor.Cancellable;
import akka.actor.UntypedActor;
import model.GlobalDate;
import model.Model;
import model.opta.OptaXmlUtils;
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

    }

    private void updateDate(Date currentDate) {
        _state.simulationDate = currentDate;
        saveStateToDB();

        GlobalDate.setFakeDate(_state.simulationDate);
    }

    private void saveStateToDB() {
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
        _tickCancellable = getContext().system().scheduler().scheduleOnce(Duration.create(1, TimeUnit.SECONDS), getSelf(), "Tick",
                                                                          getContext().dispatcher(), null);
    }

    SimulatorState _state;
    Cancellable _tickCancellable;

    static private class SimulatorState {
        public String  stateId = "--SimulatorState--";
        public Date    pauseDate;
        public Date    simulationDate;
        public int     speedFactor = MAX_SPEED;

        public SimulatorState() {}
    }
}
