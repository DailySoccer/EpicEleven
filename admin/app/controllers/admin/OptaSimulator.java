package controllers.admin;

import model.*;
import model.opta.OptaProcessor;
import org.jdom2.input.JDOMParseException;
import play.Logger;
import play.db.DB;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Date;
import java.util.HashSet;

/**
 * Created by gnufede on 13/06/14.
 */
public class OptaSimulator implements Runnable {
    private OptaSimulator(String competitionId) {

        this._isFinished = false;
        this._lastParsedDate = new Date();
        //Let's simulate just world cup
        this._competitionId = competitionId!=null? competitionId: "4";
        this._stopLoop = false;
        this._pauseLoop = true;
        this._nextDocToParseIndex = 0;
        this._optaProcessor = new OptaProcessor();

        OptaSimulatorState.initialize(this);
    }

    public static boolean start() {

        boolean wasResumed = true;

        // Es la primera vez q lo creamos?
        if (_instance == null) {
            _instance = new OptaSimulator(null);
            wasResumed = false;

            _instance._pauseLoop = false;
            _instance.startThread();
        }
        else {
            _instance._pauseLoop = false;

            if (_instance._optaThread == null || !_instance._optaThread.isAlive()) {
                _instance.startThread();
            }
        }

        return wasResumed;
    }

    static public void nextStep() {
        if (_instance == null) {
            _instance = new OptaSimulator(null);
        }
        _instance.next();
    }

    static public void resetInstance() {
        if (_instance != null) {
            _instance._stopLoop = true;

            if (_instance._optaThread != null) {
                try {
                    // Esperamos a que muera para que no haga un nuevo setFakeDate despues de que salgamos de aqui!
                    _instance._optaThread.join();
                }
                catch (InterruptedException e) { }
            }

            _instance._optaThread = null;
            _instance = null;
        }

        _snapshot = null;
        GlobalDate.setFakeDate(null);
        OptaSimulatorState.reset();
    }

    static public void reset() {
        resetInstance();
        Model.resetDB();
        MockData.ensureMockDataUsers();
        OptaSimulatorState.reset();
    }

    static public boolean isSnapshotEnabled() {
        return OptaSimulatorState.getInstance().useSnapshot;
    }

    static public void useSnapshot() {
        _snapshot =  Snapshot.getLast();
        OptaSimulatorState.update();
    }

    static public void gotoDate(Date date) {
        if (_instance == null) {
            _instance = new OptaSimulator(null);
            _instance._pauseLoop = true;
            _instance.startThread();
        }
        _instance._pause = date;
         OptaSimulatorState.update();
    }

    static public Date getCurrentDate() {
        return OptaSimulatorState.getInstance().lastParsedDate;
    }

    static public String getNextStepDescription() {
        return "" + OptaSimulatorState.getInstance().nextDocToParseIndex;
    }

    static public String getNextStop() {
        String nextStop = "None";

        OptaSimulatorState state = OptaSimulatorState.getInstance();
        if (state.paused && state.pause != null) {
            nextStop = GlobalDate.formatDate(state.pause);
        }

        return nextStop;
    }

    public static boolean isCreated() {
        return _instance != null;
    }

    public static boolean isFinished() {
        return _instance == null || _instance._isFinished;
    }

    public static boolean isPaused() {
        return _instance == null || _instance._pauseLoop;
    }

    static public void pause () {
        if (_instance != null)
            _instance._pauseLoop = true;
    }

    private void startThread() {
        _optaThread = new Thread(this);
        _optaThread.start();
    }

    @Override
    public void run() {

        _stopLoop = false;

        while (!_stopLoop && !_pauseLoop) {
            boolean isFinished = next();

            // Si hemos llegado al final, nos quedamos pausados
            if (isFinished)
                _pauseLoop = true;

            checkDate();
        }

        closeConnection();

        Logger.info("Simulator reset");
    }

    private void checkDate() {
        if (_pause != null && !_lastParsedDate.before(_pause)) {
            _pauseLoop = true;
            _pause = null;

            OptaSimulatorState.update();
        }
    }

    private void updateDate(Date currentDate) {
        _lastParsedDate = currentDate;

        GlobalDate.setFakeDate(currentDate);

        if (isSnapshotEnabled()) {
            _snapshot.update(currentDate);
        }

        ModelEvents.runTasks();
    }

    private boolean isBefore(Date date) {
        return _lastParsedDate.before(date);
    }


    private boolean next() {
        if (_connection == null) {
            createConnection();
        }

        _isFinished = false;

        try {
            if (_nextDocToParseIndex % RESULTS_PER_QUERY == 0 || _optaResultSet == null) {
                if (_stmt != null) {
                    _stmt.close();
                    _stmt = null;
                }

                _stmt = _connection.createStatement(ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY);
                if (_competitionId!=null) {
                    _optaResultSet = _stmt.executeQuery("SELECT * FROM optaxml " +
                            "WHERE competition_id='" + _competitionId + "' "+
                            "ORDER BY created_at LIMIT " +
                            RESULTS_PER_QUERY + " OFFSET " + _nextDocToParseIndex + ";");
                }
                else {
                    _optaResultSet = _stmt.executeQuery("SELECT * FROM optaxml ORDER BY created_at LIMIT " +
                            RESULTS_PER_QUERY + " OFFSET " + _nextDocToParseIndex + ";");
                }
            }

            _nextDocToParseIndex += 1;

            if (_optaResultSet.next()) {
                Date createdAt = _optaResultSet.getTimestamp("created_at");

                String sqlxml = _optaResultSet.getString("xml");
                String name = _optaResultSet.getString("name");
                String feedType = _optaResultSet.getString("feed_type");

                Logger.debug(name + " " + GlobalDate.formatDate(createdAt));

                if (feedType != null) {
                    HashSet<String> changedOptaMatchEventIds = null;
                    try {
                        changedOptaMatchEventIds = _optaProcessor.processOptaDBInput(feedType, sqlxml);
                        ModelEvents.onOptaMatchEventIdsChanged(changedOptaMatchEventIds);
                    }
                    catch (JDOMParseException e) {
                        Logger.error("Failed parsing: {}", _optaResultSet.getInt("id"), e);
                    }
                }

                updateDate(createdAt);
            }
            else {
                _isFinished = true;
                Logger.info("Hemos llegado al final de la simulacion");
                GlobalDate.setFakeDate(null);

            }
        } catch (SQLException e) {
            Logger.error("WTF 1533 SQLException: ", e);
        }

        OptaSimulatorState.update();
        return _isFinished;
    }

    private void createConnection() {
        _connection = DB.getConnection();
        try {
            _connection.setAutoCommit(false);
        } catch (SQLException e) {
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

            _connection.close();
            _connection = null;
        }
        catch (SQLException e) {
            Logger.error("WTF 742 SQLException: ", e);
        }
    }

     static OptaSimulator _instance;

    Thread _optaThread;
    volatile boolean _stopLoop;
    volatile boolean _pauseLoop;

    OptaProcessor _optaProcessor;

    final int RESULTS_PER_QUERY = 500;
    Connection _connection;
    ResultSet _optaResultSet;
    Statement _stmt;

    int _nextDocToParseIndex;

    String _competitionId;
    Date _pause;
    Date _lastParsedDate;
    boolean _isFinished;

    static Snapshot _snapshot;
}
