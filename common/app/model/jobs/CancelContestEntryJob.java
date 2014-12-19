package model.jobs;

import com.mongodb.WriteResult;
import model.Contest;
import model.ContestEntry;
import model.Model;
import model.User;
import model.accounting.AccountOp;
import model.accounting.AccountingOpsCancelContestEntry;
import model.accounting.AccountingOpsEnterContest;
import org.bson.types.ObjectId;

import java.math.BigDecimal;

public class CancelContestEntryJob extends Job {
    ObjectId userId;
    ObjectId contestId;
    ObjectId contestEntryId;

    public CancelContestEntryJob() {
    }

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
            Contest contest = Contest.findOne(contestId);
            if (contest != null) {
                ContestEntry contestEntry = contest.getContestEntryWithUser(userId);
                if (contestEntry != null) {

                    // Si el contest es de pago y ha sido pagado...
                    // TODO: En el caso de que sea un contest de pago y no haya sido pagado, no permitimos su cancelación... ?
                    if (contest.entryFee > 0 && contestEntry.paidEntry) {
                        // Crear la transacción de Abandonar un Contest
                        AccountingOpsCancelContestEntry cancelContestEntryChange = new AccountingOpsCancelContestEntry(contestId, contestEntryId);
                        AccountOp accountOp = new AccountOp(userId, new BigDecimal(contest.entryFee), User.getSeqId(userId) + 1);
                        cancelContestEntryChange.accounts.add(accountOp);
                        AccountingOpsCancelContestEntry.create(cancelContestEntryChange);

                        // Marcarlo como NO pagado
                        contestEntry.setPaidEntry(false);

                        bValid = true;
                    }
                    else if (contest.entryFee == 0) {
                        // El contest es GRATIS
                        bValid = true;
                    }
                }
            }

            if (bValid) {
                // Intentamos quitar el contestEntry
                bValid = ContestEntry.remove(userId, contestId, contestEntryId);
            }

            updateState(JobState.PROCESSING, bValid ? JobState.DONE : JobState.CANCELED);
        }
    }

    public static CancelContestEntryJob create(ObjectId userId, ObjectId contestId, ObjectId contestEntryId) {
        CancelContestEntryJob job = new CancelContestEntryJob(userId, contestId, contestEntryId);
        insert(JobType.CANCEL_CONTEST_ENTRY, job);
        job.apply();
        return job;
    }
}
