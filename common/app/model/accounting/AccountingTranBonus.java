package model.accounting;

import model.Model;
import org.bson.types.ObjectId;
import org.joda.money.Money;

import java.util.List;

/**
 * AccountingTranBonus puede ser de varios tipos:
 * - BONUS: El bonus propiamente dicho
 * - BONUS_TO_CASH: La conversión de bonus a dinero "real"
 */
public class AccountingTranBonus extends AccountingTran {
    // Identificador de bonus mediante el que podremos saber si ya se ha aplicado o no
    //   Puede ser un ObjectId (p.ej. orderId), un concepto ("SIGN UP")...
    public String bonusId;

    public AccountingTranBonus() {}

    public AccountingTranBonus(String currencyCode, TransactionType bonusType, String bonusId) {
        super(currencyCode, bonusType);
        this.bonusId = bonusId;
    }

    static public AccountingTranBonus findOne (TransactionType bonusType, String bonusId) {
        return Model.accountingTransactions()
                .findOne("{type: #, bonusId: #}", bonusType, bonusId)
                .as(AccountingTranBonus.class);
    }

    static public AccountingTran create(String currencyCode, TransactionType bonusType, String bonusId, List<AccountOp> accounts) {
        AccountingTranBonus accountingOp = findOne(bonusType, bonusId);
        if (accountingOp == null) {
            accountingOp = new AccountingTranBonus(currencyCode, bonusType, bonusId);
            accountingOp.accountOps = accounts;
            accountingOp.insertAndCommit();
        }
        return accountingOp;
    }

    static public String bonusToCashId(ObjectId contestId, ObjectId userId) {
        return String.format("%s#%s", contestId.toString(), userId.toString());
    }
}