package controllers.admin;

import com.mongodb.BasicDBObject;
import com.mongodb.util.JSON;
import model.Model;
import model.ModelCoreLoop;
import model.opta.OptaDB;
import model.opta.OptaProcessor;
import play.Logger;
import play.api.libs.json.JsPath;
import play.db.DB;
//import play.libs.XML;
import org.json.XML;
import java.sql.*;
import java.util.*;
import java.util.Date;

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
    //Iterator<OptaDB> optaIterator; //TODO:DELETE
    ResultSet optaResultSet;
    Connection connection;
    Statement stmt;


    private OptaSimulator(long initialDate, long endDate, boolean fast, boolean resetOpta, String competitionId) {
        this.pauses = new TreeSet<Date>();
        this.stopLoop = false;
        this.pauseLoop = true;
        this.initialDate = initialDate;
        this.endDate = endDate;
        this.lastParsedDate = 0L;
        this.competitionId = competitionId;
        this.optaProcessor = new OptaProcessor();

        if (fast) {
            List<String> names = Model.optaDB().distinct("name").as(String.class);
            ArrayList<OptaDB> OptaDBs = new ArrayList<OptaDB>(names.size());
            for (String name: names) {
                Iterator<OptaDB> docIterator = Model.optaDB().find("{name: #, startDate: {$gte: #, $lte: #}}",
                                                                    name, initialDate, endDate)
                                                             .sort("{startDate: -1}").limit(1)
                                                             .as(OptaDB.class).iterator();
                if (docIterator.hasNext()){
                    OptaDBs.add(docIterator.next());
                }
            }
            //this.optaIterator = OptaDBs.iterator();

        }
        else {
            this.optaResultSet = getOptaResultSet();
            /*
            if (competitionId != null) {
                this.optaIterator = Model.optaDB().find("{startDate: {$gte: #, $lte: #}, headers.X-Meta-Competition-Id: #}",
                                                        initialDate, endDate, competitionId)
                                                  .sort("{startDate: 1}")
                                                  .as(OptaDB.class).iterator();
            } else {
                this.optaIterator = Model.optaDB().find("{startDate: {$gte: #, $lte: #}}",
                                                        initialDate, endDate)
                                                  .sort("{startDate: 1}")
                                                  .as(OptaDB.class).iterator();
            }
            */
        }

        if (resetOpta) {
            Model.cleanOpta();
        }
    }

    public static boolean start() {

        boolean wasResumed = true;

        // Es la primera vez q lo creamos?
        if (instance == null) {
            instance = new OptaSimulator(0L, System.currentTimeMillis(), false, true, null);
            wasResumed = false;
        }

        instance.pauseLoop = false;
        instance.startThread();

        return wasResumed;
    }


    static public void nextStep() {
        if (instance == null) {
            instance = new OptaSimulator(0L, System.currentTimeMillis(), false, true, null);
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
            instance = new OptaSimulator(0L, System.currentTimeMillis(), false, true, null);
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

    static public void resume () {
        if (instance != null)
            instance.pauseLoop = false;
    }

    private void startThread() {
        optaThread = new Thread(this);
        optaThread.start();
    }

    @Override
    public void run() {
        this.stopLoop = false;
        while (!stopLoop && (pauseLoop || next())) {
            checkDate();
        }
        // ? closeDBConnection();
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
        try {
            if (optaResultSet.next()) {
                SQLXML sqlxml = optaResultSet.getSQLXML("xml");
                Date createdAt = optaResultSet.getDate("created_at");
                String name = optaResultSet.getString("name");
                String feedType = optaResultSet.getString("feed_type");
                System.out.println(name + " " + createdAt.toString());
                BasicDBObject json = (BasicDBObject) JSON.parse(XML.toJSONObject(sqlxml.getString()).toString());
                if (feedType != null) {
                    HashSet<String> dirtyMatchEvents = optaProcessor.processOptaDBInput(feedType, json );
                    ModelCoreLoop.onOptaMatchEventsChanged(dirtyMatchEvents);
                }
                return true;
            }
            else {
                System.out.println("NULL");
            }
        } catch (SQLException e) {
            Logger.error(e.getMessage());
            e.printStackTrace();
        }
        return false;
    }

    private void closeDBConnection(){
        try {
            stmt.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private ResultSet getOptaResultSet(){
        connection = DB.getConnection();
        String selectString = "SELECT * FROM dailysoccerdb ORDER BY created_at;";
        ResultSet results = null;
        try  {
            stmt = connection.createStatement();
            results = stmt.executeQuery(selectString);
        }
        catch (java.sql.SQLException e) {
            Logger.error("SQL Exception connecting to DailySoccerDB");
            e.printStackTrace();
        }
        return results;
    }

}
