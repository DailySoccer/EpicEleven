package actors;

import akka.actor.Cancellable;
import akka.actor.UntypedActor;
import model.GlobalDate;
import model.Model;
import model.opta.OptaXmlUtils;
import org.joda.time.DateTime;
import play.Logger;
import scala.concurrent.duration.Duration;

import java.util.Date;
import java.util.concurrent.TimeUnit;

public class SimulatorActor extends UntypedActor {

    @Override public void postStop() {
        shutdown();
    }

    @Override public void onReceive(Object msg) {
        if (msg instanceof String) {
            onReceive((String)msg);
        }
        else if (msg instanceof MessageEnvelope) {
            onReceive((MessageEnvelope)msg);
        }
        else {
            unhandled(msg);
        }
    }

    private void onReceive(MessageEnvelope msg) {
        switch (msg.msg) {
            case "GotoDate":
                gotoDate((Date)msg.params);
                break;
            case "SetSpeedFactor":
                setSpeedFactor((int)msg.params);
                break;
            default:
                unhandled(msg);
                break;
        }
    }

    private void onReceive(String msg) {

        switch (msg) {
            case "InitShutdown":
                if (_state == null) {
                    init();
                }
                else {
                    shutdown();
                }
                break;

            case "SimulatorTick":
                onSimulatorTick(false);
                break;

            case "PauseResume":
                pauseResume();
                break;

            case "NextStep":
                onSimulatorTick(true);
                break;

            case "GetSimulatorState":
                // Si no estamos inicializados, SimulatorState.isInit() == false; Para asegurar la immutabilidad, hacemos
                // una copia del SimulatorState. Seria mejor sin embargo hacer la propia clase immutable.
                sender().tell(new SimulatorState(_state), self());
                break;

            default:
                unhandled(msg);
                break;
        }
    }

    private void shutdown() {
        Logger.debug("SimulatorActor: Shutdown {}", GlobalDate.getCurrentDateString());
        cancelTicking();
        _state = null;
        GlobalDate.setFakeDate(null);
    }

    private void pauseResume() {
        if (_state == null) {
           init();
        }

        if (_state.isPaused) {
            resume();
        }
        else {
            pause();
        }
    }

    private void gotoDate(Date date) {
        if (_state == null) {
            init();
        }

        if (new DateTime(date).isBefore(new DateTime(_state.simulationDate))) {
            return;
        }

        _state.pauseDate = date;
        resume();
    }

    private void pause() {
        Logger.debug("SimulatorActor: pausing at {}", GlobalDate.getCurrentDateString());

        _state.isPaused = true;
        cancelTicking();
    }

    private void resume() {
        Logger.debug("SimulatorActor: resuming at {}", GlobalDate.getCurrentDateString());

        _state.isPaused = false;
        getSelf().tell("SimulatorTick", getSelf());
    }

    private void setSpeedFactor(int speedFactor) {
        _state.speedFactor = speedFactor;
    }

    private void init() {
        Logger.debug("SimulatorActor: initialization at {}", GlobalDate.getCurrentDateString());

        Date lastProcessedDate = Model.actors().tellAndAwait("OptaProcessorActor", "GetLastProcessedDate");

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

            // Tenemos registrada una fecha antigua de pausa? La olvidamos...
            if (isPastPauseDate(_state.simulationDate)) {
                _state.pauseDate = null;
            }
        }

        // Siempre arrancamos pausados
        _state.isPaused = true;

        // Grabamos a la DB
        updateDateAndSaveState(_state.simulationDate);

        Logger.debug("SimulatorActor: initialized, the current date is {}", GlobalDate.getCurrentDateString());
    }

    private boolean isPastPauseDate(Date date) {
        return _state.pauseDate != null &&
               new DateTime(date).isAfter(new DateTime(_state.pauseDate));
    }

    private void onSimulatorTick(boolean onlyNextStep) {
        // Es posible que nos encolen un Shutdown o un PauseResume mientras procesabamos el Tick, donde al final (unas
        // lineas mas abajo) encolamos el siguiente tick. Por lo tanto, nos puede llegar un Tick despues de un Shutdown/Pause.
        if (_state == null) {
            return;
        }

        Logger.debug("SimulatorActor: tick at {}", GlobalDate.getCurrentDateString());

        advanceOrPause(getNextDate());

        // El orden de entrega de estos mensajes no esta garantizado, como debe de ser.
        Model.actors().tell("ContestsActor", "SimulatorTick");
        Model.actors().tell("TransactionsActor", "SimulatorTick");
        Model.actors().tell("OptaProcessorActor", "SimulatorTick");

        if (!_state.isPaused && !onlyNextStep) {
            reescheduleTick();
        }
    }

    private void advanceOrPause(Date toDate) {

        // getNextDate retorna null cuando no hay siguiente documento
        if (toDate == null) {
            return;
        }

        // Nos pilla la fecha de pausa antes de la fecha a la que nos gustaria saltar?
        if (isPastPauseDate(toDate)) {
            // Si... Saltamos justo a la fecha de pausa
            Date finalDate = _state.pauseDate;
            _state.pauseDate = null;

            updateDateAndSaveState(finalDate);
            pause();
        }
        else {
            // No... Podemos saltar a la fecha destino directamente
            updateDateAndSaveState(toDate);
        }
    }

    private Date getNextDate() {
        Date ret;

        if (_state.speedFactor != SimulatorState.MAX_SPEED) {
            ret = new DateTime(_state.simulationDate).plusMillis(TICK_PERIOD * _state.speedFactor).toDate();
        }
        else {
            ret = Model.actors().tellAndAwait("OptaProcessorActor", "GetNextDoc");

            if (ret.compareTo(new Date(0L)) == 0) {
                ret = null;
            }
        }

        return ret;
    }

    private void updateDateAndSaveState(Date currentDate) {

        _state.simulationDate = currentDate;
        GlobalDate.setFakeDate(_state.simulationDate);

        // Unico punto donde grabamos nuestro estado a la DB
        Model.simulator().update("{stateId: #}", _state.stateId).upsert().with(_state);
    }

    private void cancelTicking() {
        if (_tickCancellable != null) {
            _tickCancellable.cancel();
            _tickCancellable = null;
        }
    }

    private void reescheduleTick() {

        if (_state.speedFactor != SimulatorState.MAX_SPEED) {
            _tickCancellable = getContext().system().scheduler().scheduleOnce(Duration.create(TICK_PERIOD, TimeUnit.MILLISECONDS),
                                                                              getSelf(), "SimulatorTick",
                                                                              getContext().dispatcher(), null);
        }
        else {
            // Encolamos el siguiente tick para ejecucion inmediata!
            getSelf().tell("SimulatorTick", getSelf());
        }
    }

    SimulatorState _state;
    Cancellable _tickCancellable;

    static final int TICK_PERIOD = 1000;   // Milliseconds
}
