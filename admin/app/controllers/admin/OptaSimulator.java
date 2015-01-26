package controllers.admin;

import actors.OptaProcessorActor;
import akka.actor.ActorRef;
import akka.actor.ActorSelection;
import akka.pattern.Patterns;
import akka.util.Timeout;
import model.GlobalDate;
import model.MockData;
import model.Model;
import model.opta.OptaXmlUtils;
import org.joda.time.DateTime;
import org.joda.time.Duration;
import play.Logger;
import play.libs.Akka;
import scala.concurrent.Await;
import scala.concurrent.Future;

import java.util.Date;
import java.util.concurrent.TimeUnit;

public class OptaSimulator implements Runnable {

    static public final int MAX_SPEED = -1;

    static public boolean       isCreated() { return _instance != null;  }
    static public OptaSimulator instance()  { return _instance; }

    static public void init() {
        if (_instance != null)
            throw new RuntimeException("WTF 495");

        _instance = new OptaSimulator();
    }

    static public void shutdown() {
        _instance.pause();
        _instance = null;
        GlobalDate.setFakeDate(null);

        Akka.system().actorSelection("/user/OptaProcessorActor").tell("SimulatorShutdown", ActorRef.noSender());
    }

    private OptaSimulator() {
        _stopSignal = false;

        _state = Model.simulator().findOne().as(OptaSimulatorState.class);

        Date lastProcessedDate = OptaProcessorActor.getLastProcessedDate();

        if (_state == null) {
            _state = new OptaSimulatorState();

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
        _paused = true;
        updateDate(_state.simulationDate);

        sendToOptaProcessor("SimulatorStart");
    }

    private void sendToOptaProcessor(String msg) {
        Timeout timeout = new Timeout(scala.concurrent.duration.Duration.create(10, TimeUnit.SECONDS));
        ActorSelection actorRef = Akka.system().actorSelection("/user/OptaProcessorActor");

        Future<Object> response = Patterns.ask(actorRef, msg, timeout);

        try {
            _nextDocMsg = (OptaProcessorActor.NextDocMsg)Await.result(response, timeout.duration());
        }
        catch(Exception e) {
            Logger.error("WTF 5620 sendToOptaProcessor Timeout");
        }
    }

    public Date getNextStop() { return _state.pauseDate; }
    public boolean isPaused() { return (_paused || _stopSignal);  }
    public String getNextStepDesc() { return String.valueOf(_nextDocMsg.id); }

    public void start() {
        if (_optaThread == null) {
            _optaThread = new Thread(this);
            _optaThread.start();
        }
    }

    public void pause() {
        _stopSignal = true;

        if (_optaThread != null) {
            try {
                // Tenemos que esperar a que muera, cuando salimos de aqui estamos seguros de que estamos pausados
                _optaThread.join();
            }
            catch (InterruptedException e) { }
        }
    }

    public void reset() {
        pause();

        Model.resetMongoDB();
        MockData.ensureMockDataUsers();
        MockData.ensureCompetitions();

        _instance = new OptaSimulator();
    }

    public void gotoDate(Date date) {
        _state.pauseDate = date;
        start();
    }

    @Override
    public void run() {
        _stopSignal = false;

        _paused = false;
        saveState();

        while (!_stopSignal) {

            if (_state.pauseDate != null && !_state.simulationDate.before(_state.pauseDate)) {
                _stopSignal = true;
                _state.pauseDate = null;
            }
            else {
                boolean isFinished = nextStep(_state.speedFactor);

                if (isFinished) {
                    _stopSignal = true;
                }
            }
        }

        // Salir del bucle implica que el thread muere y por lo tanto estamos pausados
        _optaThread = null;
        _stopSignal = false;
        _paused = true;
        saveState();

        Logger.info("Paused at: {}", GlobalDate.formatDate(_state.simulationDate));
    }

    private void updateDate(Date currentDate) {
        _state.simulationDate = currentDate;

        GlobalDate.setFakeDate(_state.simulationDate);

        saveState();
    }

    public boolean nextStep(int speedFactor) {

        if (_nextDocMsg.isNull() || GlobalDate.getCurrentDate().equals(_nextDocMsg.date)) {
            sendToOptaProcessor("SimulatorTick");
        }

        if (_nextDocMsg.isNotNull()) {
            try {
                Duration deltaTime = sleepUntil(_nextDocMsg.date, speedFactor);
                updateDate(new DateTime(GlobalDate.getCurrentDate()).plus(deltaTime).toDate());

                Akka.system().actorSelection("/user/InstantiateConstestsActor").tell("SimulatorTick", ActorRef.noSender());
                Akka.system().actorSelection("/user/GivePrizesActor").tell("SimulatorTick", ActorRef.noSender());
                Akka.system().actorSelection("/user/TransactionsActor").tell("SimulatorTick", ActorRef.noSender());
            }
            catch (InterruptedException e) {
                Logger.error("WTF 2311", e);
            }
        }
        else {
            Logger.info("Hemos llegado al ultimo documento XML");
        }

        return _nextDocMsg.isNull();
    }

    public void setSpeedFactor(int speedFactor) {
       _state.speedFactor = speedFactor;
       saveState();
    }

    public int getSpeedFactor() {
        return _state.speedFactor;
    }

    private Duration sleepUntil(Date nextStop, int speedFactor) throws InterruptedException {
        Duration timeToAdd;
        Duration timeUntilNextStop = new Duration(new DateTime(GlobalDate.getCurrentDate()), new DateTime(nextStop));

        if (speedFactor == MAX_SPEED) {
            timeToAdd = timeUntilNextStop;
        }
        else {
            Duration timeToSleep = SLEEPING_DURATION;
            timeToAdd = new Duration(SLEEPING_DURATION.getMillis() * speedFactor);

            if (timeUntilNextStop.compareTo(timeToAdd) < 0) {
                timeToSleep = new Duration(timeUntilNextStop.getMillis() / speedFactor);
                timeToAdd = timeUntilNextStop;
            }

            if (timeToSleep.getMillis() != 0) {
                Thread.sleep(timeToSleep.getMillis());
            }
        }
        return timeToAdd;
    }


    private void saveState() {
        Model.simulator().update("{stateId: #}", _state.stateId).upsert().with(_state);
    }

    static final Duration SLEEPING_DURATION = new Duration(1000);

    Thread _optaThread;
    volatile boolean _paused;
    volatile boolean _stopSignal;

    OptaProcessorActor.NextDocMsg _nextDocMsg;
    OptaSimulatorState _state;

    static OptaSimulator _instance;

    static private class OptaSimulatorState {
        public String  stateId = "--unique id--";
        public Date    pauseDate;
        public Date    simulationDate;
        public int     speedFactor = MAX_SPEED;

        public OptaSimulatorState() {}
    }
}

