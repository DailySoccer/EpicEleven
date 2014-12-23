package model.jobs;

import com.google.common.collect.ImmutableList;
import com.mongodb.MongoException;
import model.Contest;
import model.ContestEntry;
import model.Model;
import model.User;
import model.accounting.AccountOp;
import model.accounting.AccountingOpCancelContestEntry;
import org.bson.types.ObjectId;
import play.Logger;

import java.math.BigDecimal;

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

            boolean bValid = false;

            // Verificamos que el usuario está en el contest
            Contest contest = Contest.findOneFromContestEntry(contestEntryId);
            if (contest != null) {
                // Intentamos quitar el contestEntry
                bValid = transactionRemove();

                if (bValid) {
                    // Realizamos la gestión del payment
                    bValid = (contest.entryFee > 0) ? transactionPayment(contest.entryFee) : true;
                }
            }
            else {
                // El usuario ya no estaba en el contest
                bValid = true;
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

    private boolean transactionRemove() {
        boolean transactionValid = false;

        try {
            Contest contest = Model.contests()
                    .findAndModify("{_id: #, state: \"ACTIVE\", contestEntries._id: #, contestEntries.userId: #}", contestId, contestEntryId, userId)
                    .with("{$pull: {contestEntries: {_id: #}}}", contestEntryId)
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

    private boolean transactionPayment(int entryFee) {
        // Crear la transacción de Abandonar un Contest
        AccountingOpCancelContestEntry.create(contestId, contestEntryId, ImmutableList.of(
                new AccountOp(userId, new BigDecimal(entryFee), User.getSeqId(userId) + 1)
        ));
        return true;
    }

    public static CancelContestEntryJob create(ObjectId userId, ObjectId contestId, ObjectId contestEntryId) {
        CancelContestEntryJob job = new CancelContestEntryJob(userId, contestId, contestEntryId);
        insert(JobType.CANCEL_CONTEST_ENTRY, job);
        job.apply();
        return job;
    }
}
