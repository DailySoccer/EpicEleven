package model.accounting;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectWriter;
import model.GlobalDate;
import model.Model;
import org.bson.types.ObjectId;
import org.jongo.marshall.jackson.oid.Id;
import utils.ListUtils;
import utils.ObjectIdMapper;

import java.util.*;

@JsonTypeInfo(use=JsonTypeInfo.Id.CLASS,property="_class")
public class AccountingTran {
    public enum TransactionType {
        PRIZE,
        ORDER,
        ENTER_CONTEST,
        CANCEL_CONTEST_ENTRY,
        CANCEL_CONTEST,
        REFUND,
        FREE_MONEY,
        BONUS,
        BONUS_TO_CASH
    }

    public enum TransactionProc {
        COMMITTED,
        UNCOMMITTED
    }

    public enum TransactionState {
        VALID,
        CANCELLED
    }

    @Id
    public ObjectId accountingTranId;

    public TransactionProc proc;
    public TransactionState state;
    public TransactionType type;
    public List<AccountOp> accountOps = new ArrayList<>();

    public Date createdAt;

    public AccountingTran() {}

    public AccountingTran(TransactionType type) {
        this.proc = TransactionProc.UNCOMMITTED;
        this.state = TransactionState.VALID;
        this.type = type;
        this.createdAt = GlobalDate.getCurrentDate();
    }

    public TransactionType getTransactionType() {
        return type;
    }

    public Map<String, String> getAccountInfo(ObjectId accountId) {
        Map<String, String> info = new HashMap<>();

        AccountOp op = getAccountOp(accountId);
        if (op != null) {
            info.put("accountingTranId", accountingTranId.toString());
            info.put("type", type.name());
            info.put("value", op.value.toString());
            info.put("createdAt", String.valueOf(createdAt.getTime()));
        }

        return info;
    }

    public AccountOp getAccountOp(ObjectId accountId) {
        AccountOp accountOp = null;
        for (AccountOp op : accountOps) {
            if (op.accountId.equals(accountId)) {
                accountOp = op;
                break;
            }
        }
        return accountOp;
    }

    public static List<AccountingTran> findAllFromUserId(ObjectId userId) {
        return ListUtils.asList(Model.accountingTransactions().find("{state: \"VALID\", \"accountOps.accountId\": #}", userId).as(AccountingTran.class));
    }

    public void insertAndCommit() {
        Model.accountingTransactions().insert(this);
        commit();
    }

    public boolean commit () {
        boolean valid = true;
        for (AccountOp accountOp: accountOps) {
            if (!accountOp.canCommit()) {
                valid = false;
                break;
            }
            accountOp.updateBalance();
        }
        if (valid) {
            Model.accountingTransactions().update(accountingTranId).with("{$set: {proc: #}}", TransactionProc.COMMITTED);
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
