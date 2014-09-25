package controllers.admin;

import jobs.OptaProcessorJob;
import model.*;
import model.opta.OptaProcessor;
import model.opta.OptaXmlUtils;
import org.apache.commons.dbutils.DbUtils;
import org.joda.time.DateTime;
import org.joda.time.Duration;
import play.Logger;
import play.db.DB;

import java.sql.*;
import java.util.Date;

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
    }

    private OptaSimulator() {
        _stopSignal = false;
        _optaProcessor = new OptaProcessor();

        _state = Model.simulator().findOne().as(OptaSimulatorState.class);

        if (_state == null) {
            _state = new OptaSimulatorState();

            _state.useSnapshot = false;
            _state.simulationDate = OptaProcessorJob.getLastProcessedDate();

            // Cuando todavia nadie ha procesado ningun documento, nos ponemos X segundos antes del primero q haya
            if (_state.simulationDate.equals(new Date(0L))) {
                _state.simulationDate = new DateTime(OptaXmlUtils.getFirstDate()).minusSeconds(5).toDate();
            }

            saveState();
        }
        else {

            // Reseteamos la fecha de simulacion en caso de que el proceso haya avanzado por su cuenta
            if (_state.simulationDate.before(OptaProcessorJob.getLastProcessedDate())) {
                _state.simulationDate = OptaProcessorJob.getLastProcessedDate();
            }

            // Tenemos registrada una fecha antigua de pausa?
            if (_state.pauseDate != null && !_state.simulationDate.before(_state.pauseDate)) {
                _state.pauseDate = null;
            }

            if (_state.useSnapshot) {
                _snapshot = Snapshot.instance();
            }
        }

        // Siempre comenzamos pausados
        _paused = true;

        updateDate(_state.simulationDate);
    }

    public Date getNextStop() { return _state.pauseDate; }
    public boolean isPaused() { return (_paused || _stopSignal);  }
    public boolean isSnapshotEnabled() { return _state.useSnapshot; }

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

    public void reset(boolean useSnapshot) {
        pause();

        Model.resetDB();
        MockData.ensureMockDataUsers();
        MockData.ensureCompetitions();

        _instance = new OptaSimulator();

        if (useSnapshot) {
            _instance.useSnapshot();
        }
    }

    private void useSnapshot() {
        _snapshot = Snapshot.instance();
        _state.useSnapshot = true;
        saveState();
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
                boolean bFinished = nextStep(_state.speedFactor);

                if (bFinished) {
                    _stopSignal = true;
                }
            }
        }

        closeConnection();

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

        if (_snapshot != null) {
            _snapshot.update(_state.simulationDate);
        }

        ModelEvents.runTasks();
    }

    public boolean nextStep(int speedFactor) {
        boolean bFinished = false;

        ensureConnection();

        try {
            boolean bNewResultSet = queryNextResultSet();

            if (bNewResultSet) {
                advanceToNextDocumentInResultSet();
            }

            if (_nextDocDate != null) {
                try {
                    Duration deltaTime = sleepUntil(_nextDocDate, speedFactor);
                    updateDate(new DateTime(GlobalDate.getCurrentDate()).plus(deltaTime).toDate());

                    if (GlobalDate.getCurrentDate().equals(_nextDocDate) && !_stopSignal) {

                        // Tickeamos el OptaProcessorJob como si fueramos un proceso scheduleado
                        OptaProcessorJob.processCurrentDocumentInResultSet(_optaResultSet, _optaProcessor);

                        // Y dejamos el puntero en el siguiente documento
                        advanceToNextDocumentInResultSet();
                    }
                }
                catch (InterruptedException e) {
                    Logger.error("WTF 2311", e);
                }
            }
            else {
                bFinished = true;
                closeConnection();
                Logger.info("Hemos llegado al final de la simulacion");
            }
        }
        catch (SQLException e) {
            Logger.error("WTF 1533", e);
        }

        saveState();

        return bFinished;
    }

    private void advanceToNextDocumentInResultSet() throws SQLException {
        if (_optaResultSet.next()) {
            _nextDocDate = _optaResultSet.getTimestamp("created_at");
        }
        else {
            _nextDocDate = null;
        }
    }

    private boolean queryNextResultSet() throws SQLException {
        boolean bNewResultSet = false;

        if (_optaResultSet == null || _optaResultSet.isAfterLast()) {

            DbUtils.closeQuietly(null, _stmt, _optaResultSet);
            _stmt = null;
            _optaResultSet = null;

            Date lastProcessedDate = OptaProcessorJob.getLastProcessedDate();

            _stmt = _connection.createStatement(ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY);
            _optaResultSet = _stmt.executeQuery("SELECT * FROM optaxml WHERE created_at > '"
                                                + new Timestamp(lastProcessedDate.getTime()) +
                                                "' ORDER BY created_at LIMIT " + RESULTS_PER_QUERY + ";");
            bNewResultSet = true;
        }

        return bNewResultSet;
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

    private void ensureConnection() {

        if (_connection != null) {
            return;
        }

        _connection = DB.getConnection();
    }

    private void closeConnection() {
        DbUtils.closeQuietly(_connection, _stmt, _optaResultSet);
        _connection = null;
        _stmt = null;
        _optaResultSet = null;
    }

    private void saveState() {
        Model.simulator().update("{stateId: #}", _state.stateId).upsert().with(_state);
    }

    Thread _optaThread;
    volatile boolean _paused;
    volatile boolean _stopSignal;

    final int RESULTS_PER_QUERY = 500;
    Connection _connection;
    ResultSet _optaResultSet;
    Statement _stmt;

    Date _nextDocDate;
    static final Duration SLEEPING_DURATION = new Duration(1000);

    OptaProcessor _optaProcessor;
    Snapshot _snapshot;
    OptaSimulatorState _state;

    static OptaSimulator _instance;

    static private class OptaSimulatorState {
        public String  stateId = "--unique id--";
        public boolean useSnapshot;
        public Date    pauseDate;
        public Date    simulationDate;
        public int     speedFactor = MAX_SPEED;

        public OptaSimulatorState() {}
    }
}

