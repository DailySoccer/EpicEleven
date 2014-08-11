package controllers.admin;

import model.*;
import model.opta.OptaProcessor;
import org.jdom2.input.JDOMParseException;
import org.jongo.MongoCollection;
import play.Logger;
import play.db.DB;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HashSet;
import java.util.TimeZone;

public class OptaSimulator implements Runnable {

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
        _stopLoop = false;
        _optaProcessor = new OptaProcessor();

        _state = collection().findOne().as(OptaSimulatorState.class);

        if (_state == null) {
            _state = new OptaSimulatorState();

            _state.useSnapshot = false;
            _state.paused = true;
            _state.lastParsedDate = Model.dateFirstFromOptaXML();

            _state.competitionId = "4";     // Let's simulate just the World Cup
            _state.nextDocToParseIndex = 0;

            saveState();
        }

        updateDate(_state.lastParsedDate);
    }

    public Date getNextStop() { return _state.pauseDate; }
    public String getNextStepDescription() { return "" + _state.nextDocToParseIndex; }
    public boolean isPaused() { return _state.paused; }
    public boolean isSnapshotEnabled() { return _state.useSnapshot; }

    public void start() {
        if (_optaThread == null) {
            _optaThread = new Thread(this);
            _optaThread.start();
        }
    }

    public void pause() {
        _stopLoop = true;

        if (_optaThread != null) {
            try {
                // Tenemos que esperar a que muera, cuando salimos de aqui estamos seguros de que estamos pausados
                _optaThread.join();
            }
            catch (InterruptedException e) { }
        }
    }

    public void continueFromSnapshot() {
        pause();

        Snapshot.load();

        _state = collection().findOne().as(OptaSimulatorState.class);
        _state.useSnapshot = false;
        _state.paused = true;
        _snapshot = null;

        saveState();
        updateDate(_state.lastParsedDate);
    }

    public void reset(boolean useSnapshot) {
        pause();

        Model.resetDB();
        MockData.ensureMockDataUsers();

        _instance = new OptaSimulator();

        if (useSnapshot) {
            _snapshot = Snapshot.getLast();
            _state.useSnapshot = true;
            saveState();
        }
    }

    public void gotoDate(Date date) {
        _state.pauseDate = date;
        start();
    }

    @Override
    public void run() {
        _stopLoop = false;

        _state.paused = false;
        saveState();

        while (!_stopLoop) {

            if (_state.pauseDate != null && !_state.lastParsedDate.before(_state.pauseDate)) {
                _stopLoop = true;
                _state.pauseDate = null;
            }
            else {
                boolean bFinished = nextStep();

                if (bFinished) {
                    _stopLoop = true;
                }
            }
        }

        closeConnection();

        // Salir del bucle implica que el thread muere y por lo tanto estamos pausados
        _optaThread = null;
        _state.paused = true;
        saveState();

        Logger.info("Paused at: {}", GlobalDate.formatDate(_state.lastParsedDate));
    }

    private void updateDate(Date currentDate) {
        _state.lastParsedDate = currentDate;

        GlobalDate.setFakeDate(_state.lastParsedDate);

        if (_state.useSnapshot && _snapshot != null) {
            _snapshot.update(_state.lastParsedDate);
        }

        ModelEvents.runTasks();
    }

    public boolean nextStep() {
        boolean bFinished = false;

        if (_connection == null) {
            createConnection();
        }

        try {
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
            }

            if (_optaResultSet.next()) {
                Date createdAt = _optaResultSet.getTimestamp("created_at", new GregorianCalendar(TimeZone.getTimeZone("UTC")));

                String sqlxml = _optaResultSet.getString("xml");
                String name = _optaResultSet.getString("name");
                String feedType = _optaResultSet.getString("feed_type");

                Logger.debug(name + " " + GlobalDate.formatDate(createdAt));

                if (feedType != null) {
                    try {
                        HashSet<String> changedOptaMatchEventIds = _optaProcessor.processOptaDBInput(feedType, sqlxml);
                        ModelEvents.onOptaMatchEventIdsChanged(changedOptaMatchEventIds);
                    }
                    catch (JDOMParseException e) {
                        Logger.error("Failed parsing: {}", _optaResultSet.getInt("id"), e);
                    }
                }

                _state.nextDocToParseIndex++;
                updateDate(createdAt);
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

    private void createConnection() {
        _connection = DB.getConnection();
        try {
            _connection.setAutoCommit(false);
        }
        catch (SQLException e) {
            Logger.error("WTF 1231 SQLException: ", e);
        }
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
    volatile boolean _stopLoop;

    final int RESULTS_PER_QUERY = 500;
    Connection _connection;
    ResultSet _optaResultSet;
    Statement _stmt;

    OptaProcessor _optaProcessor;
    Snapshot _snapshot;
    OptaSimulatorState _state;

    static OptaSimulator _instance;
}

class OptaSimulatorState {
    public String  stateId = "--unique id--";
    public String  competitionId;
    public boolean useSnapshot;
    public boolean paused;
    public Date    pauseDate;
    public Date    lastParsedDate;
    public int     nextDocToParseIndex;

    public OptaSimulatorState() {}
}
