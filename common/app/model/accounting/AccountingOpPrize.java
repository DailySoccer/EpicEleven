package model.accounting;

import com.mongodb.WriteResult;
import model.Model;
import org.bson.types.ObjectId;

import java.util.List;

public class AccountingOpPrize extends AccountingOp {
    public ObjectId contestId;

    public AccountingOpPrize() {
    }

    public AccountingOpPrize(ObjectId contestId) {
        super(TransactionType.PRIZE);
        this.contestId = contestId;
    }

    static public AccountingOp create(ObjectId contestId, List<AccountOp> accounts) {
        AccountingOpPrize accountingOp = new AccountingOpPrize(contestId);
        accountingOp.accounts = accounts;
        WriteResult result = Model.accountingTransactions().update("{type: #, contestId: #}",
                accountingOp.type, accountingOp.contestId).upsert().with(accountingOp);
        if (result.getN() > 0) {
            play.Logger.info(accountingOp.toJson());
        }
        return accountingOp;
    }
}
