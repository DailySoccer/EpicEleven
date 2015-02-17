package model.jobs;

import com.google.common.collect.ImmutableList;
import com.mongodb.MongoException;
import model.Contest;
import model.ContestEntry;
import model.Model;
import model.User;
import model.accounting.AccountOp;
import model.accounting.AccountingTran;
import model.accounting.AccountingTranCancelContestEntry;
import org.bson.types.ObjectId;
import org.joda.money.Money;
import play.Logger;

public class CancelContestEntryJob extends Job {
    ObjectId userId;
    ObjectId contestId;
    ObjectId contestEntryId;

    public CancelContestEntryJob() {}

    private CancelContestEntryJob(ObjectId userId, ObjectId contestId, ObjectId contestEntryId) {
        this.userId = userId;
        this.contestId = contestId;
        this.contestEntryId = contestEntryId;
    }

    @Override
    public void apply() {
        if (state.equals(JobState.TODO)) {
            updateState(JobState.TODO, JobState.PROCESSING);
        }

        if (state.equals(JobState.PROCESSING)) {

            boolean bValid;

            // Verificamos que el usuario está en el contest
            Contest contest = Contest.findOneFromContestEntry(contestEntryId);
            if (contest != null) {
                // Intentamos quitar el contestEntry
                bValid = transactionRemoveContestEntry();
            }
            else {
                // Aunque no esté el contestEntry incluido en el contest
                //  puede que tengamos una tarea pendiente
                contest = Model.contests().findOne("{_id: #, pendingJobs: {$in: [#]}}", contestId, jobId).as(Contest.class);
                bValid = (contest != null);
            }

            // Únicamente el job que haya sido el que haya quitado el contestEntry será
            //  el que gestione la devolución del pago
            if (bValid) {
                // Realizamos la gestión del payment
                bValid = contest.entryFee.isNegativeOrZero() || transactionReturnPayment(contest.entryFee);

                if (!bValid)
                    throw new RuntimeException("WTF 131313: CancelContestEntry invalid");

                // Quitar la tarea pendiente
                Model.contests().update("{_id: #}", contestId).with("{$pull: {pendingJobs: #}}", jobId);
            }

            updateState(JobState.PROCESSING, bValid ? JobState.DONE : JobState.CANCELED);
        }
    }

    @Override
    public void continueProcessing() {
        if (state.equals(JobState.TODO) || state.equals(JobState.CANCELING)) {
            updateState(state, JobState.CANCELED);
            return;
        }

        apply();
    }

    private boolean transactionRemoveContestEntry() {
        boolean transactionValid = false;

        try {
            Contest contest = Model.contests()
                    .findAndModify("{_id: #, state: \"ACTIVE\", contestEntries._id: #, contestEntries.userId: #}", contestId, contestEntryId, userId)
                    .with("{$pull: {contestEntries: {_id: #}}, $addToSet: {pendingJobs: #}}", contestEntryId, jobId)
                    .as(Contest.class);

            if (contest != null) {
                // Registrar el contestEntry eliminado
                ContestEntry cancelledContestEntry = contest.findContestEntry(contestEntryId);
                Model.cancelledContestEntries().insert(cancelledContestEntry);

                transactionValid = true;
            }
        }
        catch (MongoException exc) {
            Logger.error("WTF 7801: ", exc);
        }

        return transactionValid;
    }

    private boolean transactionReturnPayment(Money entryFee) {
        // Crear la transacción de Abandonar un Contest
        AccountingTran accountingTran = AccountingTranCancelContestEntry.create(contestId, contestEntryId, ImmutableList.of(
                new AccountOp(userId, entryFee, User.getSeqId(userId) + 1)
        ));
        return accountingTran != null;
    }

    public static CancelContestEntryJob create(ObjectId userId, ObjectId contestId, ObjectId contestEntryId) {
        CancelContestEntryJob job = new CancelContestEntryJob(userId, contestId, contestEntryId);
        insert(JobType.CANCEL_CONTEST_ENTRY, job);
        job.apply();
        return job;
    }
}
