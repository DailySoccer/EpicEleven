package controllers.admin;

import com.mongodb.BasicDBObject;
import model.Model;
import model.ModelCoreLoop;
import model.opta.OptaDB;
import model.opta.OptaProcessor;

import java.util.*;

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
    Iterator<OptaDB> optaIterator;

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
            this.optaIterator = OptaDBs.iterator();
        }
        else {
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
        OptaDB nextDoc = optaIterator.hasNext()? optaIterator.next(): null;

        if (nextDoc != null) {
            System.out.println(nextDoc.name + " " + (new Date(nextDoc.startDate)).toString());

            this.lastParsedDate = nextDoc.startDate;
            String feedType = nextDoc.getFeedType();

            if (feedType != null) {
                HashSet<String> dirtyMatchEvents = optaProcessor.processOptaDBInput(feedType, (BasicDBObject) nextDoc.json);
                ModelCoreLoop.onOptaMatchEventsChanged(dirtyMatchEvents);
            }
        }
        else {
            System.out.println("NULL");
        }
        return nextDoc != null;
    }
}
