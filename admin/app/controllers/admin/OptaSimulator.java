package controllers.admin;

import model.GlobalDate;
import model.MockData;
import model.Model;
import model.ModelEvents;
import model.Snapshot;
import model.opta.OptaProcessor;
import org.jdom2.input.JDOMParseException;
import play.Logger;
import play.db.DB;

import java.sql.*;
import java.util.Date;
import java.util.HashSet;

/**
 * Created by gnufede on 13/06/14.
 */
public class OptaSimulator implements Runnable {
    static OptaSimulator _instance;

    Thread _optaThread;
    volatile boolean _stopLoop;
    volatile boolean _pauseLoop;
    Date _pause;
    OptaProcessor _optaProcessor;

    long _lastParsedDate;
    String _competitionId;

    final int RESULTS_PER_QUERY = 500;
    Connection _connection;
    ResultSet _optaResultSet;
    Statement _stmt;
    int _nextDocToParseIndex;
    boolean _isFinished;


    private OptaSimulator(String competitionId) {
        this._isFinished = false;
        this._stopLoop = false;
        this._pauseLoop = true;
        this._lastParsedDate = 0L;
        this._nextDocToParseIndex = 0;
        this._competitionId = competitionId;
        this._optaProcessor = new OptaProcessor();

        createConnection();
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

            if (_instance._optaThread == null) {
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

    static public void reset() {
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
        Model.resetDB();
        MockData.ensureMockDataUsers();
    }

    static public boolean isSnapshotEnabled() {
        return _snapshot != null;
    }

    static public void useSnapshot(Snapshot aSnapshot) {
        _snapshot = aSnapshot;
    }

    static public void gotoDate(Date date) {
        if (_instance == null) {
            _instance = new OptaSimulator(null);
            _instance._pauseLoop = true;
            _instance.startThread();
        }
        _instance._pause = date;
    }

    static public String getNextStepDescription() {
        return _instance != null? "" + _instance._nextDocToParseIndex : "0";
    }

    static public String getNextStop() {
        return (_instance==null)? "None": (_instance._pause==null)? "None": _instance._pause.toString();
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

        this._stopLoop = false;

        while (!_stopLoop) {
            if (!_pauseLoop) {
                boolean isFinished = next();

                // Si hemos llegado al final, nos quedamos pausados
                if (isFinished)
                    _pauseLoop = true;

                checkDate();
            }
            else {                       // Durante la pausa reevaluamos cada X ms si continuamos
                try {
                    Thread.sleep(10);
                }  catch (InterruptedException e) {}
            }
        }

        closeConnection();

        Logger.info("Simulator reset");
    }

    private void checkDate() {
        if (_pause != null && _lastParsedDate >= _pause.getTime()) {
            _pauseLoop = true;
            _pause = null;
        }
    }

    private void updateDate(Date currentDate) {
        GlobalDate.setFakeDate(currentDate);

        if (isSnapshotEnabled()) {
            _snapshot.update(currentDate);
        }

        ModelEvents.runTasks();
    }

    private boolean isBefore(long date) {
        return _lastParsedDate < date;
    }


    private boolean next() {
        _isFinished = false;

        try {
            if (_nextDocToParseIndex % RESULTS_PER_QUERY == 0) {
                if (_stmt != null) {
                    _stmt.close();
                }

                _stmt = _connection.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_READ_ONLY);
                _optaResultSet = _stmt.executeQuery("SELECT * FROM optaxml ORDER BY created_at LIMIT " +
                                                  RESULTS_PER_QUERY + " OFFSET " + _nextDocToParseIndex + ";");
            }

            _nextDocToParseIndex += 1;

            if (_optaResultSet.next()) {
                String sqlxml = _optaResultSet.getString("xml");
                Date createdAt = _optaResultSet.getTimestamp("created_at");
                _lastParsedDate = createdAt.getTime();
                String name = _optaResultSet.getString("name");
                String feedType = _optaResultSet.getString("feed_type");

                Logger.debug(name + " " + createdAt.toString());

                if (feedType != null) {
                    HashSet<String> changedOptaMatchEventIds = null;
                    try {
                        changedOptaMatchEventIds = _optaProcessor.processOptaDBInput(feedType, sqlxml);
                        ModelEvents.onOptaMatchEventIdsChanged(changedOptaMatchEventIds);
                    } catch (JDOMParseException e) {
                        Logger.error("Failed parsing: {}", _optaResultSet.getInt("id"));

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

    static Snapshot _snapshot;
}
