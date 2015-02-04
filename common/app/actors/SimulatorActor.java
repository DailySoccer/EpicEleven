package actors;

import akka.actor.Cancellable;
import akka.actor.UntypedActor;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import model.GlobalDate;
import model.MockData;
import model.Model;
import model.opta.OptaXmlUtils;
import org.joda.time.DateTime;
import play.Logger;
import scala.concurrent.duration.Duration;

import java.util.Date;
import java.util.concurrent.TimeUnit;

public class SimulatorActor extends UntypedActor {

    static public final int MAX_SPEED = -1;

    static public class GotoDateMsg {
        public Date date;

        public GotoDateMsg(Date d) { date = d; }
    }

    static public class SpeedFactorMsg {
        public int speedFactor;
    }

    @Override public void postStop() {
        shutdown();
    }

    @Override public void onReceive(Object msg) {
        if (msg instanceof String) {
            onReceive((String)msg);
        }
        else if (msg instanceof GotoDateMsg) {
            gotoDate(((GotoDateMsg)msg).date);
        }
        else if (msg instanceof SpeedFactorMsg) {
            setSpeedFactor(((SpeedFactorMsg) msg).speedFactor);
        }
        else {
            unhandled(msg);
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
                onSimulatorTick();
                break;

            case "PauseResume":
                pauseResume();
                break;

            case "Reset":
                shutdown();
                Model.resetMongoDB();
                MockData.ensureMockDataUsers();
                MockData.ensureCompetitions();
                init();
                break;

            case "GetSimulatorState":
                // Si no estamos inicializados, SimulatorState.isInit() == false;
                sender().tell(new SimulatorState(_state), self());
                break;

            default:
                unhandled(msg);
                break;
        }
    }

    private void shutdown() {
        Logger.debug("SimulatorActor: Shutdown {}", GlobalDate.getCurrentDate());
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

        _state.pauseDate = date;
        resume();
    }

    private void pause() {
        Logger.debug("SimulatorActor: pausing at {}", GlobalDate.getCurrentDate());

        _state.isPaused = true;
        cancelTicking();
    }

    private void resume() {
        Logger.debug("SimulatorActor: resuming at {}", GlobalDate.getCurrentDate());

        _state.isPaused = false;
        getSelf().tell("SimulatorTick", getSelf());
    }

    private void setSpeedFactor(int speedFactor) {
        _state.speedFactor = speedFactor;
    }

    private void init() {
        Logger.debug("SimulatorActor: initialization at {}", GlobalDate.getCurrentDate());

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
            if (_state.pauseDate != null && _state.pauseDate.before(_state.simulationDate)) {
                _state.pauseDate = null;
            }
        }

        // Siempre arrancamos pausados
        _state.isPaused = true;

        // Grabamos a la DB
        updateDateAndSaveState(_state.simulationDate);

        Logger.debug("SimulatorActor: initialized, the current date is {}", GlobalDate.getCurrentDate());
    }

    private boolean breakOnPause() {
        boolean bBreak = false;

        if (_state.pauseDate != null && _state.pauseDate.before(_state.simulationDate)) {
            Logger.debug("SimulatorActor: pausing at requested date {}", _state.pauseDate);

            pause();

            _state.pauseDate = null;
            updateDateAndSaveState(_state.simulationDate);

            bBreak = true;
        }

        return bBreak;
    }

    private void onSimulatorTick() {
        Logger.debug("SimulatorActor: tick at {}", GlobalDate.getCurrentDate());

        // Es posible que nos encolen un Shutdown o un PauseResume mientras procesabamos el Tick, donde al final (unas
        // lineas mas abajo) encolamos el siguiente tick. Por lo tanto, nos puede llegar un Tick despues de un Shutdown/Pause.
        // Miramos ademas si tenemos que parar debido a que hemos sobrepasado la fecha de pausa.
        if (_state == null || _state.isPaused || breakOnPause()) {
            return;
        }

        if (_state.speedFactor != MAX_SPEED) {
            int virtualElapsedTime = TICK_PERIOD * _state.speedFactor;

            updateDateAndSaveState(new DateTime(_state.simulationDate).plusMillis(virtualElapsedTime).toDate());

            reescheduleTick();
        }
        else {
            // Apuntamos la GlobalDate exactamente a la del siguiente documento
            OptaProcessorActor.NextDoc nextdocMsg = (OptaProcessorActor.NextDoc)Model.getDailySoccerActors()
                                                        .tellToActorAwaitResult("OptaProcessorActor", "GetNextDoc");

            // Si al OptaProcessorActor no le ha dado tiempo a cargar el siguiente documento, simplemente esperamos al siguiente tick
            if (nextdocMsg.isNotNull()) {
                updateDateAndSaveState(nextdocMsg.date);
            }

            // Encolamos el siguiente tick para ejecucion inmediata!
            getSelf().tell("SimulatorTick", getSelf());
        }

        // El orden de entrega de estos mensajes no esta garantizado, como debe de ser.
        Model.getDailySoccerActors().tellToActor("OptaProcessorActor", "SimulatorTick");
        Model.getDailySoccerActors().tellToActor("InstantiateConstestsActor", "SimulatorTick");
        Model.getDailySoccerActors().tellToActor("CloseContestsActor", "SimulatorTick");
        Model.getDailySoccerActors().tellToActor("TransactionsActor", "SimulatorTick");
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
        _tickCancellable = getContext().system().scheduler().scheduleOnce(Duration.create(TICK_PERIOD, TimeUnit.MILLISECONDS), getSelf(), "SimulatorTick",
                                                                          getContext().dispatcher(), null);
    }

    SimulatorState _state;
    Cancellable _tickCancellable;

    static final int TICK_PERIOD = 1000;   // Milliseconds

    static public class SimulatorState {
        static final String UNIQUE_ID = "--SimulatorState--";

        public String  stateId = UNIQUE_ID;
        public Date    simulationDate;
        public Date    pauseDate;
        public boolean isPaused;
        public int     speedFactor = MAX_SPEED;

        public SimulatorState() { }
        public SimulatorState(SimulatorState o) {
            if (o != null) {
                this.stateId = o.stateId;
                this.simulationDate = o.simulationDate;
                this.pauseDate = o.pauseDate;
                this.isPaused = o.isPaused;
                this.speedFactor = o.speedFactor;
            }
        }

        @JsonSerialize
        public boolean isInit() { return simulationDate != null; }

        @JsonSerialize
        public Date   getCurrentDate() { return GlobalDate.getCurrentDate(); }

        @JsonSerialize
        public String getCurrentDateFormatted() { return GlobalDate.getCurrentDateString(); }
        @JsonSerialize
        public String getSimulationDateFormatted() { return (simulationDate != null)? GlobalDate.formatDate(simulationDate) : ""; }
        @JsonSerialize
        public String getPauseDateFormatted() { return (pauseDate != null)? GlobalDate.formatDate(pauseDate) : ""; }
    }
}
