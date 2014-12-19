package actors;

import akka.actor.UntypedActor;
import model.GlobalDate;
import model.Model;
import model.accounting.TransactionOp;
import play.Logger;
import scala.concurrent.duration.Duration;
import utils.ListUtils;

import java.util.List;
import java.util.concurrent.TimeUnit;

public class TransactionsActor extends UntypedActor {
    public void onReceive(Object msg) {

        switch ((String)msg) {

            case "Tick":
                onTick();
                getContext().system().scheduler().scheduleOnce(Duration.create(30, TimeUnit.MINUTES), getSelf(),
                        "Tick", getContext().dispatcher(), null);
                break;

            // En el caso del SimulatorTick no tenemos que reeschedulear el mensaje porque es el Simulator el que se
            // encarga de drivearnos.
            case "SimulatorTick":
                onTick();
                break;

            default:
                unhandled(msg);
                break;
        }
    }

    private void onTick() {
        Logger.info("Transactions: {}", GlobalDate.getCurrentDateString());

        /*
        *   Una transacción pasará de Uncommitted a Committed cuando verifique que todas las anteriores transacciones
        *   de sus "accounts" han sido realizadas (anteriores AccountOp.seqId en estado Committed)
        *   Toda "account" en estado Committed tendrá el AccountOp.cachedBalance correctamente actualizado
         */
        List<TransactionOp> transactionOps = ListUtils.asList(Model.accountingTransactions().find("{proc: #, state: #}",
                TransactionOp.TransactionProc.UNCOMMITTED, TransactionOp.TransactionState.VALID).as(TransactionOp.class));
        for (TransactionOp transactionOp : transactionOps) {
            transactionOp.commit();
        }
    }}
