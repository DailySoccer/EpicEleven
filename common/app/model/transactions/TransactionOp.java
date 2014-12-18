package model.transactions;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.mongodb.WriteResult;
import model.Model;
import org.jongo.marshall.jackson.oid.Id;
import org.bson.types.ObjectId;
import utils.ObjectIdMapper;

public class TransactionOp {
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

    public TransactionOp() {}

    private TransactionOp(TransactionType type) {
        this.proc = TransactionProc.UNCOMMITTED;
        this.state = TransactionState.VALID;
        this.type = type;
    }

    public boolean commit () {
        boolean valid = true;
        for (AccountOp accountOp: changes.accounts) {
            if (!accountOp.canCommit()) {
                valid = false;
                break;
            }
            accountOp.updateBalance();
        }
        if (valid) {
            Model.transactions().update(transactionId).with("{$set: {proc: #}}", TransactionProc.COMMITTED);
        }
        return valid;
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

    public static TransactionOp createPrizeTransaction(TransactionOpPrize prizeChange) {
        TransactionOp transactionOp = new TransactionOp(TransactionType.PRIZE);
        transactionOp.changes = prizeChange;
        WriteResult result = Model.transactions().update("{type: #, 'changes.contestId': #}", transactionOp.type, prizeChange.contestId).upsert().with(transactionOp);
        if (result.getN() > 0) {
            play.Logger.info(transactionOp.toJson());
        }
        return transactionOp;
    }
}
