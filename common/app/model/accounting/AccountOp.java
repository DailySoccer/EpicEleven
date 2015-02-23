package model.accounting;

import model.Model;
import model.Product;
import model.User;
import org.bson.types.ObjectId;
import org.joda.money.CurrencyUnit;
import org.joda.money.Money;
import utils.MoneyUtils;

import java.util.List;

public class AccountOp {
    public ObjectId accountId;          // Identificador del "account" (actualmente corresponde a un userId)
    public Money value;                 // Cantidad a modificar el balance
    public Integer seqId;               // Secuencia de operaciones de un "account" determinado
    public Money cachedBalance;         // El balance del "account" (en el momento que se hizo el "commit")

    public AccountOp() {}

    public AccountOp(ObjectId accountId, Money value, Integer seqId) {
        this.accountId = accountId;
        this.value = value;
        this.seqId = seqId;
    }

    public boolean canCommit() {
        // Comprobamos que NO exista ninguna transacción "anterior" ("seqId" menor) de la misma "account" sin "commit"
        return Model.accountingTransactions()
                .count("{ accountOps: {$elemMatch: { accountId: #, seqId: { $lt: # } }}, proc: #}",
                        accountId, seqId, AccountingTran.TransactionProc.UNCOMMITTED) == 0;
    }

    public void updateBalance() {
        // Obtenemos el balance del anterior COMMIT
        Money lastBalance = MoneyUtils.plus(getLastBalance(), value);
        // Actualizamos el cachedBalance del "account"
        Model.accountingTransactions().update("{ accountOps: { $elemMatch: {accountId: #, seqId: #} } }", accountId, seqId).with("{$set: {\"accountOps.$.cachedBalance\": #}}", lastBalance.toString());
        // Actualizamos el user
        User.updateBalance(accountId, lastBalance);
    }

    public Money getLastBalance() {
        // Si es el primer seqId no existe ninguno anterior
        if (seqId == 1) {
            return Money.zero(Product.CURRENCY_DEFAULT);
        }

        // TODO: ¿necesitamos comprobar que el commit es del "seqId" inmediatamente anterior?
        List<AccountOp> accountOp = Model.accountingTransactions()
                .aggregate("{$match: { \"accountOps.accountId\": #, proc: #, state: \"VALID\"}}", accountId, AccountingTran.TransactionProc.COMMITTED)
                .and("{$unwind: \"$accountOps\"}")
                .and("{$match: {\"accountOps.accountId\": #}}", accountId)
                .and("{$project: { \"accountOps.seqId\": 1, \"accountOps.cachedBalance\": 1 }}")
                .and("{$sort: { \"accountOps.seqId\": -1 }}")
                .and("{$limit: 1}")
                .and("{$group: {_id: \"balance\", accountId: { $first: \"$accountOps.accountId\" }, cachedBalance: { $first: \"$accountOps.cachedBalance\" }}}")
                .as(AccountOp.class);

        return (!accountOp.isEmpty()) ? MoneyUtils.withCurrencyUnit(accountOp.get(0).cachedBalance) : Money.zero(Product.CURRENCY_DEFAULT);
    }
}
