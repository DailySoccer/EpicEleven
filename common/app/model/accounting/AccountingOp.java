package model.accounting;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.mongodb.WriteResult;
import model.Model;
import org.jongo.marshall.jackson.oid.Id;
import org.bson.types.ObjectId;
import utils.ObjectIdMapper;

import java.util.ArrayList;
import java.util.List;

@JsonTypeInfo(use=JsonTypeInfo.Id.CLASS,property="_class")
public class AccountingOp {
    public enum TransactionType {
        PRIZE,
        ORDER,
        ENTER_CONTEST,
        CANCEL_CONTEST_ENTRY,
        CANCEL_CONTEST
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
    public List<AccountOp> accounts = new ArrayList<>();

    public AccountingOp() {}

    public AccountingOp(TransactionType type) {
        this.proc = TransactionProc.UNCOMMITTED;
        this.state = TransactionState.VALID;
        this.type = type;
    }

    public boolean commit () {
        boolean valid = true;
        for (AccountOp accountOp: accounts) {
            if (!accountOp.canCommit()) {
                valid = false;
                break;
            }
            accountOp.updateBalance();
        }
        if (valid) {
            Model.accountingTransactions().update(transactionId).with("{$set: {proc: #}}", TransactionProc.COMMITTED);
        }
        return valid;
    }

    public String toJson() {
        String json = "";
        try {
            ObjectWriter ow = new ObjectIdMapper().writer().withDefaultPrettyPrinter();
            json = ow.writeValueAsString(this);
        } catch (JsonProcessingException e) {
            e.printStackTrace();
        }
        return json;
    }
}
