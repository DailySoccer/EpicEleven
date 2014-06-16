package controllers.admin;

import com.mongodb.BasicDBObject;
import model.Model;
import model.opta.OptaDB;
import utils.OptaUtils;

/**
 * Created by gnufede on 13/06/14.
 */
public class OptaSimulator extends Thread {
    static OptaSimulator instance;
    long initialDate;
    long endDate;
    String competitionId;
    Iterable<OptaDB> optaDBCollection;
    boolean stopLoop;


    public static OptaSimulator getInstance () {
        if (instance == null){
            instance = new OptaSimulator();
        }
        return instance;
    }

    public static boolean existsInstance () {
        return instance != null;
    }

    public OptaSimulator () {
    }

    public void launch(long initialDate, long endDate, String competitionId){
        this.initialDate = initialDate;
        this.endDate = endDate;
        this.competitionId = competitionId;
        this.stopLoop = false;
        if (competitionId != null){
            this.optaDBCollection = Model.optaDB().find("{startDate: {$gte: #, $lte: #}, headers.X-Meta-Competition-Id: #}",
                    initialDate, endDate, competitionId).sort("{startDate: 1}").as(OptaDB.class);
        } else {
            this.optaDBCollection = Model.optaDB().find("{startDate: {$gte: #, $lte: #}}",
                    initialDate, endDate).sort("{startDate: 1}").as(OptaDB.class);
        }
    }

    public void run () {
        Model.resetOpta();
        while (!stopLoop && next()){
            try {
                sleep(1);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

    }

    public void halt () {
        this.stopLoop = true;
        this.stop();
    }

    public boolean next (){
        OptaDB nextDoc = optaDBCollection.iterator().hasNext()? optaDBCollection.iterator().next(): null;
        if (nextDoc != null){
            System.out.println(nextDoc.name);
            String feedType = nextDoc.getFeedType();
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
