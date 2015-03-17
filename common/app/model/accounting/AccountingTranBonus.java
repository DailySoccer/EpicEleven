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
    public Money bonus;

    public AccountingTranBonus() {}

    public AccountingTranBonus(TransactionType bonusType, String bonusId, Money bonus) {
        super(bonusType);
        this.bonusId = bonusId;
        this.bonus = bonus;
    }

    static public AccountingTranBonus findOne (TransactionType bonusType, String bonusId) {
        return Model.accountingTransactions()
                .findOne("{type: #, bonusId: #}", bonusType, bonusId)
                .as(AccountingTranBonus.class);
    }

    static public AccountingTran create(TransactionType bonusType, String bonusId, Money bonus, List<AccountOp> accounts) {
        AccountingTranBonus accountingOp = findOne(bonusType, bonusId);
        if (accountingOp == null) {
            accountingOp = new AccountingTranBonus(bonusType, bonusId, bonus);
            accountingOp.accountOps = accounts;
            accountingOp.insertAndCommit();
        }
        return accountingOp;
    }
}