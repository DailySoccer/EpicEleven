package controllers.admin;

import model.Global;
import model.Model;
import model.ModelCoreLoop;
import model.opta.OptaProcessor;
import play.Logger;
import play.db.DB;

import java.sql.*;
import java.util.Date;
import java.util.HashSet;
import java.util.TreeSet;

/**
 * Created by gnufede on 13/06/14.
 */
public class OptaSimulator implements Runnable {
    static OptaSimulator instance;

    Thread optaThread;
    volatile boolean stopLoop;
    volatile boolean pauseLoop;
    TreeSet<Date> pauses;
    OptaProcessor optaProcessor;

    long initialDate;
    long endDate;
    long lastParsedDate;
    String competitionId;

    final int RESULTS_PER_QUERY = 500;
    Connection connection;
    ResultSet optaResultSet;
    Statement stmt;
    int nextDocToParseIndex;


    private OptaSimulator(long initialDate, long endDate, boolean resetDB, String competitionId) {
        this.pauses = new TreeSet<Date>();
        this.stopLoop = false;
        this.pauseLoop = true;
        this.initialDate = initialDate;
        this.endDate = endDate;
        this.lastParsedDate = 0L;
        this.nextDocToParseIndex = 0;
        this.competitionId = competitionId;
        this.optaProcessor = new OptaProcessor();

        createConnection();

        if (resetDB) {
            Model.resetDB();
        }
    }

    public static boolean start() {

        boolean wasResumed = true;

        // Es la primera vez q lo creamos?
        if (instance == null) {
            instance = new OptaSimulator(0L, System.currentTimeMillis(), true, null);
            wasResumed = false;

            instance.pauseLoop = false;
            instance.startThread();
        }
        else {
            instance.pauseLoop = false;
        }

        return wasResumed;
    }


    static public void nextStep() {
        if (instance == null) {
            instance = new OptaSimulator(0L, System.currentTimeMillis(), true, null);
        }
        instance.next();
    }

    static public void reset() {
        if (instance != null) {
            instance.stopLoop = true;
            instance.optaThread = null;
            instance = null;
        }
    }

    static public void addPause(Date date) {
        if (instance == null) {
            instance = new OptaSimulator(0L, System.currentTimeMillis(), true, null);
            instance.pauseLoop = true;
            instance.startThread();
        }
        instance.pauses.add(date);
    }

    public static boolean isPaused() {
        return instance == null || instance.pauseLoop;
    }

    static public void pause () {
        if (instance != null)
            instance.pauseLoop = true;
    }

    private void startThread() {
        optaThread = new Thread(this);
        optaThread.start();
    }

    @Override
    public void run() {

        this.stopLoop = false;

        while (!stopLoop) {
            if (!pauseLoop) {
                boolean isFinished = next();

                // Si hemos llegado al final, nos quedamos pausados
                if (isFinished)
                    pauseLoop = true;

                checkDate();
            }
            else {                       // Durante la pausa reevaluamos cada X ms si continuamos
                try {
                    Thread.sleep(10);
                }  catch (InterruptedException e) {};
            }
        }

        closeConnection();

        Logger.info("Simulator reset");
    }

    private void checkDate() {
        if (!pauses.isEmpty() && lastParsedDate >= pauses.first().getTime()) {
            pauseLoop = true;
            pauses.remove(pauses.first());
        }
    }

    private boolean isBefore(long date) {
        return lastParsedDate < date;
    }

    private boolean next() {
        boolean isFinished = false;

        try {
            if (nextDocToParseIndex % RESULTS_PER_QUERY == 0) {
                if (stmt != null) {
                    stmt.close();
                }

                stmt = connection.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_READ_ONLY);
                optaResultSet = stmt.executeQuery("SELECT * FROM dailysoccerdb ORDER BY created_at LIMIT " +
                                                  RESULTS_PER_QUERY + " OFFSET " + nextDocToParseIndex + ";");
            }

            nextDocToParseIndex += 1;

            if (optaResultSet.next()) {
                SQLXML sqlxml = optaResultSet.getSQLXML("xml");
                Date createdAt = optaResultSet.getTimestamp("created_at");
                lastParsedDate = createdAt.getTime();
                String name = optaResultSet.getString("name");
                String feedType = optaResultSet.getString("feed_type");

                Logger.debug(name + " " + createdAt.toString());

                if (feedType != null) {
                    //BasicDBObject json = (BasicDBObject) JSON.parse(XML.toJSONObject(sqlxml.getString()).toString());


                    HashSet<String> dirtyMatchEvents = optaProcessor.processOptaDBInput(feedType, sqlxml.getString());
                    //HashSet<String> dirtyMatchEvents = optaProcessor.processOptaDBInput(feedType, json);
                    ModelCoreLoop.onOptaMatchEventsChanged(dirtyMatchEvents);
                }
                Global.setFakeTime(createdAt);
            }
            else {
                isFinished = true;
                Logger.info("Hemos llegado al final de la simulacion");
            }
        } catch (SQLException e) {
            Logger.error("WTF 1533 SQLException: ", e);
        }

        return isFinished;
    }

    private void createConnection() {
        connection = DB.getConnection();
        try {
            connection.setAutoCommit(false);
        } catch (SQLException e) {
            Logger.error("WTF 1231 SQLException: ", e);
        }
    }

    private void closeConnection() {
        try {
            if (stmt != null) {
                stmt.close();

                stmt = null;
                optaResultSet = null;
            }

            connection.close();
            connection = null;
        }
        catch (SQLException e) {
            Logger.error("WTF 742 SQLException: ", e);
        }
    }
}
