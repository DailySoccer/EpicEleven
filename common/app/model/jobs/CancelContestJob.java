package model.jobs;

import com.mongodb.WriteResult;
import model.*;
import model.accounting.AccountOp;
import model.accounting.AccountingTran;
import model.accounting.AccountingTranCancelContest;
import org.bson.types.ObjectId;
import org.joda.money.CurrencyUnit;
import org.joda.money.Money;
import utils.MoneyUtils;

import java.util.ArrayList;
import java.util.List;

public class CancelContestJob extends Job {
    ObjectId contestId;

    public CancelContestJob() {}

    private CancelContestJob(ObjectId contestId) {
        this.contestId = contestId;
    }

    @Override
    public void apply() {
        if (state.equals(Job.JobState.TODO)) {
            updateState(Job.JobState.TODO, Job.JobState.PROCESSING);
        }

        if (state.equals(Job.JobState.PROCESSING)) {

            boolean bValid = false;

            Contest contest = Contest.findOne(contestId);
            if (contest != null) {
                if (!contest.state.isCanceled()) {
                    // Cancelamos el contest si sigue sin estar Cancelado y sin estar Lleno
                    // Una vez marcado como Cancelado, volvemos a leer el contest actualizado, para garantizar que tenemos los contestEntries correctos
                    contest = Model.contests()
                            .findAndModify("{_id: #, state: { $ne: \"CANCELED\" }, freeSlots: {$gt: 0}}", contestId)
                            .with("{$set: {state: \"CANCELED\", canceledAt: #}, $addToSet: {pendingJobs: #}}", GlobalDate.getCurrentDate(), jobId)
                            .returnNew()
                            .as(Contest.class);
                    bValid = (contest != null);
                }
                else {
                    // Si lo ha cancelado este job, tendremos que finalizar lo que empezamos
                    bValid = (Model.contests().count("{_id: #, pendingJobs: {$in: [#]}}", contestId, jobId) == 1);
                }

                if (bValid) {
                    // Únicamente devolveremos el GOLD (no la ENERGY)
                    if (contest.entryFee.getCurrencyUnit().equals(MoneyUtils.CURRENCY_GOLD) && MoneyUtils.isGreaterThan(contest.entryFee, MoneyUtils.zero) && !contest.contestEntries.isEmpty()) {
                        List<AccountOp> accounts = new ArrayList<>();
                        for (ContestEntry contestEntry : contest.contestEntries) {
                            accounts.add(new AccountOp(contestEntry.userId, contest.entryFee, User.getSeqId(contestEntry.userId) + 1));
                        }
                        AccountingTran accountingTran = AccountingTranCancelContest.create(contest.entryFee.getCurrencyUnit().getCode(), contestId, accounts);
                        bValid = (accountingTran != null);
                    }

                    // Quitar la tarea pendiente
                    Model.contests().update("{_id: #}", contestId).with("{$pull: {pendingJobs: #}}", jobId);
                }
            }

            updateState(Job.JobState.PROCESSING, bValid ? Job.JobState.DONE : Job.JobState.CANCELED);
        }
    }

    @Override
    public void continueProcessing() {
        apply();
    }

    public static CancelContestJob create(ObjectId contestId) {
        CancelContestJob job = new CancelContestJob(contestId);
        insert(JobType.CANCEL_CONTEST, job);
        job.apply();
        return job;
    }
}
