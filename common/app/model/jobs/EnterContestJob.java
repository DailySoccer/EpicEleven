package model.jobs;

import com.google.common.collect.ImmutableList;
import model.Contest;
import model.ContestEntry;
import model.User;
import model.accounting.AccountOp;
import model.accounting.AccountingOpEnterContest;
import org.bson.types.ObjectId;

import java.math.BigDecimal;
import java.util.List;

public class EnterContestJob extends Job {
    ObjectId userId;
    ObjectId contestId;
    List<ObjectId> soccerIds;

    public EnterContestJob() {
    }

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
                            AccountingOpEnterContest.create(contestId, contestEntry.contestEntryId, ImmutableList.of(
                                    new AccountOp(userId, new BigDecimal(-contest.entryFee), seqId)
                            ));

                            // Marcarlo como pagado
                            contestEntry.setPaidEntry(true);

                            // Se ha realizado el pago correctamente
                            bValid = true;
                        }
                    } else {
                        // El contest O es GRATIS O ha sido PAGADO
                        bValid = true;
                    }
                }
            }

            updateState(JobState.PROCESSING, bValid ? JobState.DONE : JobState.CANCELING);
        }

        if (state.equals(JobState.CANCELING)) {

            // Verificamos si el usuario ha sido incluido en el contest
            Contest contest = Contest.findOne(contestId);
            if (contest != null) {
                ContestEntry contestEntry = contest.getContestEntryWithUser(userId);
                if (contestEntry != null) {
                    // Lo quitamos del contest
                    ContestEntry.remove(userId, contestId, contestEntry.contestEntryId);
                }
            }

            updateState(JobState.CANCELING, JobState.CANCELED);
        }
    }

    public static EnterContestJob create(ObjectId userId, ObjectId contestId, List<ObjectId> soccersIds) {
        EnterContestJob job = new EnterContestJob(userId, contestId, soccersIds);
        insert(JobType.ENTER_CONTEST, job);
        job.apply();
        return job;
    }
}