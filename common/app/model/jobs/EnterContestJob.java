package model.jobs;

import com.mongodb.WriteResult;
import model.Contest;
import model.ContestEntry;
import model.Model;
import model.User;
import model.accounting.AccountOp;
import model.accounting.AccountingOpsEnterContest;
import org.bson.types.ObjectId;

import java.math.BigDecimal;
import java.util.List;

public class EnterContestJob extends Job {
    ObjectId userId;
    ObjectId contestId;
    List<ObjectId> soccerIds;

    private EnterContestJob(ObjectId userId, ObjectId contestId, List<ObjectId> soccersIds) {
        this.userId = userId;
        this.contestId = contestId;
        this.soccerIds = soccersIds;
    }

    @Override
    public void apply() {
        if (state.equals(JobState.TODO)) {
            updateState(JobState.TODO, JobState.PROCESSING);
        }

        if (state.equals(JobState.PROCESSING)) {

            boolean bValid = false;

            // Intentamos crear el contestEntry
            ContestEntry.create(userId, contestId, soccerIds);

            // Verificamos que el usuario haya sido incluido en el contest
            Contest contest = Contest.findOne(contestId);
            if (contest != null) {
                ContestEntry contestEntry = contest.getContestEntryWithUser(userId);
                if (contestEntry != null) {

                    // Si el contest es de pago y no ha sido pagado...
                    if (contest.entryFee > 0 && !contestEntry.paidEntry) {
                        // Registramos el seqId, de tal forma que si se produce una alteracion en el número de operaciones
                        // del usuario se lance una excepcion que impida la inserción ya que (accountId, seqId) es "unique key"
                        Integer seqId = User.getSeqId(userId) + 1;

                        // El usuario tiene dinero suficiente?
                        BigDecimal userBalance = User.calculateBalance(userId);
                        if (userBalance.compareTo(new BigDecimal(contest.entryFee)) >= 0) {
                            // Registrar el pago
                            AccountingOpsEnterContest enterContestChange = new AccountingOpsEnterContest(contestId, contestEntry.contestEntryId);
                            AccountOp accountOp = new AccountOp(userId, new BigDecimal(-contest.entryFee), seqId);
                            enterContestChange.accounts.add(accountOp);
                            AccountingOpsEnterContest.create(enterContestChange);

                            // Marcarlo como pagado
                            contestEntry.setPaidEntry(true);

                            // Se ha realizado el pago correctamente
                            bValid = true;
                        }
                    }
                    else {
                        // El contest O es GRATIS O ha sido PAGADO
                        bValid = true;
                    }
                }
            }

            updateState(JobState.PROCESSING, bValid ? JobState.DONE : JobState.CANCELED);
        }
    }

    public static EnterContestJob create(ObjectId userId, ObjectId contestId, List<ObjectId> soccersIds) {
        EnterContestJob job = new EnterContestJob(userId, contestId, soccersIds);
        insert(JobType.ENTER_CONTEST, job);
        job.apply();
        return job;
    }
}