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

    static public final int MAX_SPEED = -1;

    @Override public void postStop() {
        shutdown();
    }

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

            case "SimulatorTick":
                onTick();
                break;

            case "PauseResume":
                if (_state == null) {
                    Logger.error("WTF 7733 Recibido PauseResume sin haber inicializado");
                } else {
                    pauseResume();
                }
                break;

            case "GetSimulatorState":
                // Si no estamos inicializados, SimulatorState.isNull() == true;
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
        Model.getDailySoccerActors().tellToActor("OptaProcessorActor", "SimulatorShutdown");
    }

    private void pauseResume() {
        if (_state.isPaused) {
            _state.isPaused = false;
            getSelf().tell("SimulatorTick", getSelf());
        }
        else {
            _state.isPaused = true;
            cancelTicking();
        }
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
            if (_state.pauseDate != null && !_state.simulationDate.before(_state.pauseDate)) {
                _state.pauseDate = null;
            }
        }

        // Siempre arrancamos pausados
        _state.isPaused = true;

        // Nos ponemos en la fecha y grabamos el estado
        updateDateAndSaveState(_state.simulationDate);

        // Ponemos al OptaProcessorActor en el modo que nos interesa
        Model.getDailySoccerActors().tellToActor("OptaProcessorActor", "SimulatorInit");

        Logger.debug("SimulatorActor: initialized, the current date is {}", GlobalDate.getCurrentDate());
    }

    private void onTick() {
        Logger.debug("SimulatorActor: {}", GlobalDate.getCurrentDate());

        // Es posible que nos encolen un Shutdown o un PauseResume mientras procesabamos el Tick, donde al final (unas
        // lineas mas abajo) encolamos el siguiente tick. Por lo tanto, nos puede llegar un Tick despues de un Shutdown/PauseResume
        if (_state == null || _state.isPaused) {
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
        saveStateToDB();

        GlobalDate.setFakeDate(_state.simulationDate);
    }

    private void saveStateToDB() {
        // Unico punto donde grabamos nuestro estado a la DB
        Model.simulator().update("{stateId: #}", _state.stateId).upsert().with(_state);
    }

    /*
    private void startTicking() {
        if (_tickCancellable != null) {
            throw new RuntimeException("WTF 8722");
        }

        reescheduleTick();
    }
    */

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

        public SimulatorState() {}
        public SimulatorState(SimulatorState o) {
            if (o != null) {
                this.stateId = o.stateId;
                this.pauseDate = o.pauseDate;
                this.isPaused = o.isPaused;
                this.simulationDate = o.simulationDate;
                this.speedFactor = o.speedFactor;
            }
        }

        // Como no podemos mandar un mensaje null, lo marcamos asi
        public boolean isNull() { return simulationDate == null; }
        public boolean isNotNull() { return simulationDate != null; }
    }
}
