package model.transactions;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.mongodb.WriteResult;
import model.Model;
import org.jongo.marshall.jackson.oid.Id;
import org.bson.types.ObjectId;
import utils.ObjectIdMapper;

public class Transaction {
    public enum TransactionType {
        PRIZE
    };

    public enum TransactionProc {
        COMMITTED,
        UNCOMMITTED
    };

    public enum TransactionState {
        VALID,
        CANCELLED
    };

    @Id
    public ObjectId transactionId;

    public TransactionProc proc;
    public TransactionState state;
    public TransactionType type;
    public TransactionOps changes;

    public Transaction() {}

    private Transaction(TransactionType type) {
        this.proc = TransactionProc.UNCOMMITTED;
        this.state = TransactionState.VALID;
        this.type = type;
    }

    public void commit () {
        Model.transactions().update(transactionId).with("{$set: {proc: #}}", TransactionProc.COMMITTED);
    }

    private String toJson() {
        String json = "";
        try {
            ObjectWriter ow = new ObjectIdMapper().writer().withDefaultPrettyPrinter();
            json = ow.writeValueAsString(this);
        } catch (JsonProcessingException e) {
            e.printStackTrace();
        }
        return json;
    }

    public static Transaction createPrizeTransaction(TransactionOpPrize prizeChange) {
        Transaction transaction = new Transaction(TransactionType.PRIZE);
        transaction.changes = prizeChange;
        WriteResult result = Model.transactions().update("{type: #, 'changes.contestId': #}", transaction.type, prizeChange.contestId).upsert().with(transaction);
        if (result.getN() > 0) {
            play.Logger.info(transaction.toJson());
        }
        return transaction;
    }
}
