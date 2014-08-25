package controllers.admin;

import model.*;
import model.opta.OptaProcessor;
import org.jdom2.input.JDOMParseException;
import org.joda.time.DateTime;
import org.joda.time.Duration;
import org.jongo.MongoCollection;
import play.Logger;
import play.db.DB;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Date;
import java.util.HashSet;

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

        _state = collection().findOne().as(OptaSimulatorState.class);

        if (_state == null) {
            _state = new OptaSimulatorState();

            _state.useSnapshot = false;
            _state.lastParsedDate = new DateTime(Model.getFirstDateFromOptaXML()).minusSeconds(5).toDate();

            _state.competitionId = "4";     // Let's simulate just the World Cup
            _state.nextDocToParseIndex = 0;

            saveState();
        }
        else {
            // Tenemos registrada una fecha antigua de pausa?
            if (_state.pauseDate != null && !_state.lastParsedDate.before(_state.pauseDate)) {
                _state.pauseDate = null;
            }

            // Si estábamos usando un snapshot, habrá que inicializarlo
            if (_state.useSnapshot) {
                _snapshot = Snapshot.instance();
            }
        }

        // Siempre comenzamos pausados
        _paused = true;

        updateDate(_state.lastParsedDate);
    }

    public Date getNextStop() { return _state.pauseDate; }
    public String getNextStepDescription() { return "" + _state.nextDocToParseIndex; }
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

            if (_state.pauseDate != null && !_state.lastParsedDate.before(_state.pauseDate)) {
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

        Logger.info("Paused at: {}", GlobalDate.formatDate(_state.lastParsedDate));
    }

    private void updateDate(Date currentDate) {
        _state.lastParsedDate = currentDate;

        GlobalDate.setFakeDate(_state.lastParsedDate);

        if (_snapshot != null) {
            _snapshot.update(_state.lastParsedDate);
        }

        ModelEvents.runTasks();
    }

    public boolean nextStep(int speedFactor) {
        boolean bFinished = false;

        ensureConnection();

        try {
            queryNextResultSet();

            if (_nextDocDate != null) {
                try {
                    if (sleepUntil(_nextDocDate, speedFactor)) {
                        processNextDoc();
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
            Logger.error("WTF 1533 SQLException: ", e);
        }

        saveState();

        return bFinished;
    }

    private void processNextDoc() throws SQLException {
        if (_optaResultSet == null) {
            throw new RuntimeException("WTF 7241: processNextDoc");
        }

        Date createdAt = _optaResultSet.getTimestamp("created_at");

        String sqlxml = _optaResultSet.getString("xml");
        String name = _optaResultSet.getString("name");
        String feedType = _optaResultSet.getString("feed_type");

        Logger.debug(name + " " + GlobalDate.formatDate(createdAt));

        HashSet<String> changedOptaMatchEventIds = _optaProcessor.processOptaDBInput(feedType, sqlxml);
        ModelEvents.onOptaMatchEventIdsChanged(changedOptaMatchEventIds);

        _state.nextDocToParseIndex++;
        _nextDocDate = null;
    }

    private void queryNextResultSet() throws SQLException {
        if (_state.nextDocToParseIndex % RESULTS_PER_QUERY == 0 || _optaResultSet == null) {
            if (_stmt != null) {
                _stmt.close();
                _stmt = null;
            }

            _stmt = _connection.createStatement(ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY);

            if (_state.competitionId != null) {
                _optaResultSet = _stmt.executeQuery("SELECT * FROM optaxml " +
                                                    "WHERE competition_id='" + _state.competitionId + "' "+
                                                    "ORDER BY created_at LIMIT " +
                                                    RESULTS_PER_QUERY + " OFFSET " + _state.nextDocToParseIndex + ";");
            }
            else {
                _optaResultSet = _stmt.executeQuery("SELECT * FROM optaxml ORDER BY created_at LIMIT " +
                                                    RESULTS_PER_QUERY + " OFFSET " + _state.nextDocToParseIndex + ";");
            }

            _nextDocDate = null;
        }

        if (_nextDocDate == null && _optaResultSet.next()) {
            _nextDocDate = _optaResultSet.getTimestamp("created_at");
        }
    }

    public void setSpeedFactor(int speedFactor) {
       _state.speedFactor = speedFactor;
       saveState();
    }

    public int getSpeedFactor() {
        return _state.speedFactor;
    }

    private boolean sleepUntil(Date nextStop, int speedFactor) throws InterruptedException {

        boolean reachedStop = false;

        if (speedFactor == MAX_SPEED) {
            updateDate(nextStop);
            reachedStop = true;
        }
        else {
            Duration untilNextStop = new Duration(new DateTime(GlobalDate.getCurrentDate()), new DateTime(nextStop));
            Duration sleeping = SLEEPING_DURATION;
            Duration addedTime = new Duration(SLEEPING_DURATION.getMillis() * speedFactor);

            if (untilNextStop.compareTo(addedTime) < 0) {
                sleeping = new Duration(untilNextStop.getMillis() / speedFactor);
                addedTime = untilNextStop;
                reachedStop = true;
            }

            if (sleeping.getMillis() != 0) {
                Thread.sleep(sleeping.getMillis());
            }

            if (!_stopSignal) {
                Date nextDate = new DateTime(GlobalDate.getCurrentDate()).plus(addedTime).toDate();
                updateDate(nextDate);
            }
        }

        return reachedStop && !_stopSignal;
    }


    private void ensureConnection() {

        if (_connection != null) {
            return;
        }

        _connection = DB.getConnection();
    }

    private void closeConnection() {
        try {
            if (_stmt != null) {
                _stmt.close();

                _stmt = null;
                _optaResultSet = null;
            }

            if (_connection != null) {
                _connection.close();
                _connection = null;
            }
        }
        catch (SQLException e) {
            Logger.error("WTF 742 SQLException: ", e);
        }
    }

    private MongoCollection collection() { return Model.jongo().getCollection("simulator"); }

    private void saveState() {
        collection().update("{stateId: #}", _state.stateId).upsert().with(_state);
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
}

class OptaSimulatorState {
    public String  stateId = "--unique id--";
    public String  competitionId;
    public boolean useSnapshot;
    public Date    pauseDate;
    public Date    lastParsedDate;
    public int     nextDocToParseIndex;
    public int     speedFactor = 3600;

    public OptaSimulatorState() {}
}

