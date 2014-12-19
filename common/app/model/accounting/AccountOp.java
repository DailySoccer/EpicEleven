package model.accounting;

import model.Model;
import model.User;
import org.bson.types.ObjectId;
import java.math.BigDecimal;
import java.util.List;

public class AccountOp {
    public ObjectId accountId;          // Identificador del "account" (actualmente corresponde a un userId)
    public BigDecimal value;            // Cantidad a modificar el balance
    public Integer seqId;               // Secuencia de operaciones de un "account" determinado
    public BigDecimal cachedBalance;    // El balance del "account" (en el momento que se hizo el "commit")

    public AccountOp() {}

    public AccountOp(ObjectId accountId, BigDecimal value, Integer seqId) {
        this.accountId = accountId;
        this.value = value;
        this.seqId = seqId;
    }

    public boolean canCommit() {
        // Comprobamos que NO exista ninguna transacción "anterior" ("seqId" menor) de la misma "account" sin "commit"
        return Model.accountingTransactions()
                .count("{ \"changes.accounts\": {$elemMatch: { accountId: #, seqId: { $lt: # } }}, proc: #}",
                        accountId, seqId, AccountingOp.TransactionProc.UNCOMMITTED) == 0;
    }

    public void updateBalance() {
        // Obtenemos el balance del anterior COMMIT
        BigDecimal lastBalance = getLastBalance().add(value);
        // Actualizamos el cachedBalance del "account"
        Model.accountingTransactions().update("{ \"changes.accounts\": { $elemMatch: {accountId: #, seqId: #} } }", accountId, seqId).with("{$set: {\"changes.accounts.$.cachedBalance\": #}}", lastBalance.doubleValue());
        // Actualizamos el user
        User.updateBalance(accountId, lastBalance);
    }

    public BigDecimal getLastBalance() {
        // Si es el primer seqId no existe ninguno anterior
        if (seqId == 1) {
            return new BigDecimal(0);
        }

        // TODO: ¿necesitamos comprobar que el commit es del "seqId" inmediatamente anterior?
        List<AccountOp> accountOp = Model.accountingTransactions()
                .aggregate("{$match: { \"changes.accounts.accountId\": #, proc: #, state: \"VALID\"}}", accountId, AccountingOp.TransactionProc.COMMITTED)
                .and("{$unwind: \"$changes.accounts\"}")
                .and("{$match: {\"changes.accounts.accountId\": #}}", accountId)
                .and("{$project: { \"changes.accounts.seqId\": 1, \"changes.accounts.cachedBalance\": 1 }}")
                .and("{$sort: { \"changes.accounts.seqId\": -1 }}")
                .and("{$limit: 1}")
                .and("{$group: {_id: \"balance\", accountId: { $first: \"$changes.accounts.accountId\" }, cachedBalance: { $first: \"$changes.accounts.cachedBalance\" }}}")
                .as(AccountOp.class);

        return (!accountOp.isEmpty()) ? accountOp.get(0).cachedBalance : new BigDecimal(0);
    }
}
