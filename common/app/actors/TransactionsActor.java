package actors;

import akka.actor.UntypedActor;
import model.GlobalDate;
import model.Model;
import model.accounting.AccountingTran;
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
                getContext().system().scheduler().scheduleOnce(Duration.create(1, TimeUnit.SECONDS), getSelf(),
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
        *   de sus "accountOps" han sido realizadas (anteriores AccountOp.seqId en estado Committed)
        *   Toda "account" en estado Committed tendrá el AccountOp.cachedBalance correctamente actualizado
         */
        List<AccountingTran> accountingTrans = ListUtils.asList(Model.accountingTransactions().find("{proc: #, state: #}",
                AccountingTran.TransactionProc.UNCOMMITTED, AccountingTran.TransactionState.VALID).as(AccountingTran.class));
        for (AccountingTran accountingTran : accountingTrans) {
            accountingTran.commit();
        }

        final int MINUTES_THRESHOLD = 1;

        for (Job job: Job.findByStateAndLastModified(Job.JobState.TODO, new DateTime().minusMinutes(MINUTES_THRESHOLD).toDate())) {
            job.continueProcessing();
        }

        for (Job job: Job.findByStateAndLastModified(Job.JobState.PROCESSING, new DateTime().minusMinutes(MINUTES_THRESHOLD).toDate())) {
            job.continueProcessing();
        }

        for (Job job: Job.findByStateAndLastModified(Job.JobState.CANCELING, new DateTime().minusMinutes(MINUTES_THRESHOLD).toDate())) {
            job.continueProcessing();
        }
    }}
