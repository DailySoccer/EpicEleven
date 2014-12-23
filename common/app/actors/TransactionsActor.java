package actors;

import akka.actor.UntypedActor;
import model.GlobalDate;
import model.Model;
import model.accounting.AccountingOp;
import model.jobs.Job;
import org.joda.time.DateTime;
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
                getContext().system().scheduler().scheduleOnce(Duration.create(1, TimeUnit.MINUTES), getSelf(),
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
        List<AccountingOp> accountingOps = ListUtils.asList(Model.accountingTransactions().find("{proc: #, state: #}",
                AccountingOp.TransactionProc.UNCOMMITTED, AccountingOp.TransactionState.VALID).as(AccountingOp.class));
        for (AccountingOp accountingOp : accountingOps) {
            accountingOp.commit();
        }

        Job jobTodo = Job.findJobByStateAndLastModified(Job.JobState.TODO, new DateTime().minusMinutes(1).getMillis());
        if (jobTodo != null) {
            jobTodo.continueProcessing();
        }

        Job jobProcessing = Job.findJobByStateAndLastModified(Job.JobState.PROCESSING, new DateTime().minusMinutes(1).getMillis());
        if (jobProcessing != null) {
            jobProcessing.continueProcessing();
        }

        Job jobCanceled = Job.findJobByStateAndLastModified(Job.JobState.CANCELED, new DateTime().minusMinutes(1).getMillis());
        if (jobCanceled != null) {
            jobCanceled.continueProcessing();
        }
    }}
