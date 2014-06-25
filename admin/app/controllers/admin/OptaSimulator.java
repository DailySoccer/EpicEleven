package controllers.admin;

import com.mongodb.BasicDBObject;
import model.Model;
import model.opta.OptaDB;
import utils.OptaUtils;

import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;

/**
 * Created by gnufede on 13/06/14.
 */
public class OptaSimulator implements Runnable {
    static OptaSimulator instance;
    static Thread optaThread;
    long initialDate;
    long endDate;
    long lastParsedDate;
    int waitMillisecondsBetweenEvents;
    String competitionId;
    //Iterable<OptaDB> optaDBCollection;
    volatile boolean stopLoop;
    volatile boolean pauseLoop;
    Iterator<OptaDB> optaIterator;


    public static OptaSimulator getInstance () {
        if (instance == null){
            instance = new OptaSimulator();
        }
        return instance;
    }

    public static boolean existsInstance () {
        return instance != null;
    }

    public static boolean launchedInstance () {
        return !getInstance().stopLoop;
    }

    public static boolean pausedInstance () {
        return getInstance().pauseLoop;
    }

    public static boolean stoppedInstance () {
        return !getInstance().launchedInstance();
    }

    public OptaSimulator () {
        this.stopLoop = true;
        this.pauseLoop = true;
    }

    public void launch(long initialDate, long endDate, int waitMillisecondsBetweenEvents, boolean fast,
                       boolean resetOpta, String competitionId){
        this.stopLoop = false;
        this.pauseLoop = false;
        this.initialDate = initialDate;
        this.endDate = endDate;
        this.lastParsedDate = 0L;
        this.waitMillisecondsBetweenEvents = 1;
        this.competitionId = competitionId;
        if (fast) {
            List<String> names = Model.optaDB().distinct("name").as(String.class);
            ArrayList<OptaDB> OptaDBs = new ArrayList<OptaDB>(names.size());
            for (String name: names) {
                Iterator<OptaDB> docIterator = Model.optaDB().find("{name: #, startDate: {$gte: #, $lte: #}}",
                                                                   name, initialDate, endDate).
                                    sort("{startDate: -1}").limit(1).
                                    as(OptaDB.class).iterator();
                if (docIterator.hasNext()){
                    OptaDBs.add(docIterator.next());
                }
            }
            this.optaIterator = OptaDBs.iterator();
        }
        else {
            if (competitionId != null) {
                this.optaIterator = Model.optaDB().find("{startDate: {$gte: #, $lte: #}, headers.X-Meta-Competition-Id: #}",
                        initialDate, endDate, competitionId).sort("{startDate: 1}").as(OptaDB.class).iterator();
            } else {
                this.optaIterator = Model.optaDB().find("{startDate: {$gte: #, $lte: #}}",
                        initialDate, endDate).sort("{startDate: 1}").as(OptaDB.class).iterator();
            }
        }
        if (resetOpta) {
            Model.resetOpta();
        }
    }

    public void start() {
        optaThread = new Thread(this);
        optaThread.start();
    }

    @Override
    public void run () {
        this.stopLoop = false;
        while (!stopLoop && (pauseLoop || next())) {
            try {
                Thread.sleep(this.waitMillisecondsBetweenEvents);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

    }

    public boolean isBefore (long date){
        return lastParsedDate<date;
    }

    public void goTo (long endDate){
        while (endDate > lastParsedDate){
            next();
        }
    }

    public void stop() {
        this.halt();
        optaThread = null;
    }

    public void halt () {
        this.stopLoop = true;
    }

    public void pause () {
        this.pauseLoop = true;
    }

    public static boolean isPaused () {
        if (existsInstance()) {
            return instance.pauseLoop;
        }
        return false;
    }

    public void resumeLoop () {
        this.pauseLoop = false;
        if (this.stopLoop) {
            this.stopLoop = false;
            this.start();
        }
    }

    public void restartLoop () {
        if (this.stopLoop) {
            getInstance().start();
        }
    }

    public boolean next (){
        OptaDB nextDoc = null;
            nextDoc = optaIterator.hasNext()? optaIterator.next(): null;
        if (nextDoc != null){
            System.out.println(nextDoc.name + " " + (new Date(nextDoc.startDate)).toString());
            String feedType = nextDoc.getFeedType();
            this.lastParsedDate = nextDoc.startDate;
            if (feedType != null){
                OptaUtils.processOptaDBInput(feedType, (BasicDBObject) nextDoc.json);
            }
        }
        else {
            System.out.println("NULL");
        }
        return nextDoc != null;
    }
}
