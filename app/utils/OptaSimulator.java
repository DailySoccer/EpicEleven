package utils;

import com.mongodb.BasicDBObject;
import controllers.OptaHttpController;
import model.Model;
import model.opta.OptaDB;
import org.jongo.Find;

import java.util.ArrayList;
import java.util.Date;

/**
 * Created by gnufede on 13/06/14.
 */
public class OptaSimulator extends Thread {
    Date initialDate;
    Date endDate;
    String competitionId;
    Iterable<OptaDB> optaDBCollection;


    public OptaSimulator(Date initialDate, Date endDate, String competitionId){
        this.initialDate = initialDate;
        this.endDate = endDate;
        this.competitionId = competitionId;
        if (competitionId != null){
            this.optaDBCollection = Model.optaDB().find("{startDate: {$gte: #, $lte: #}, headers.X-Meta-Competition-Id: #}",
                                    initialDate, endDate, competitionId).sort("{startDate: 1}").as(OptaDB.class);
        } else {
            this.optaDBCollection = Model.optaDB().find("{startDate: {$gte: #, $lte: #}}",
                                    initialDate, endDate).sort("{startDate: 1}").as(OptaDB.class);
        }
    }

    public void run () {

    }

    public OptaDB next(){
        OptaDB nextDoc = optaDBCollection.iterator().hasNext()? optaDBCollection.iterator().next(): null;
        if (nextDoc != null){
            OptaHttpController.processOptaDBInput(nextDoc.headers.get("X-Meta-Feed-Type")[0], (BasicDBObject)nextDoc.json);
        }
        return nextDoc;
    }
}
