package actors;

import model.GlobalDate;
import model.Model;
import model.accounting.AccountingTran;
import model.jobs.Job;
import org.joda.time.DateTime;
import play.Logger;
import utils.ListUtils;

import java.util.Date;
import java.util.List;

public class TransactionsActor extends TickableActor {

    public void onReceive(Object msg) {

        switch ((String)msg) {
            default:
                super.onReceive(msg);
                break;
        }
    }

    @Override protected void onTick() {
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
        final Date dateThreshold = new DateTime(GlobalDate.getCurrentDate()).minusMinutes(MINUTES_THRESHOLD).toDate();

        for (Job job: Job.findByStateAndLastModified(Job.JobState.TODO, dateThreshold)) {
            job.continueProcessing();
        }

        for (Job job: Job.findByStateAndLastModified(Job.JobState.PROCESSING, dateThreshold)) {
            job.continueProcessing();
        }

        for (Job job: Job.findByStateAndLastModified(Job.JobState.CANCELING, dateThreshold)) {
            job.continueProcessing();
        }
    }
}
