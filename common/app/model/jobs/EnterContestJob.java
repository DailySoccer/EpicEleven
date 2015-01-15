package model.jobs;

import com.google.common.collect.ImmutableList;
import com.mongodb.DuplicateKeyException;
import com.mongodb.WriteResult;
import model.*;
import model.accounting.AccountOp;
import model.accounting.AccountingOp;
import model.accounting.AccountingOpCancelContestEntry;
import model.accounting.AccountingOpEnterContest;
import org.bson.types.ObjectId;

import java.math.BigDecimal;
import java.util.List;

public class EnterContestJob extends Job {
    public ObjectId userId;
    public ObjectId contestId;
    public List<ObjectId> soccerIds;

    // ContestEntry generado por este Job
    public ObjectId contestEntryId;

    public EnterContestJob() {}

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

            Contest contest = Contest.findOne(contestId);
            if (contest != null) {
                ContestEntry contestEntry = contest.getContestEntryWithUser(userId);
                if (contestEntry == null) {
                    contestEntry = createContestEntry();

                    bValid = (contest.entryFee <= 0) || transactionPayment(contest.entryFee);

                    if (bValid) {
                        bValid = transactionContestEntryInsert(contest, contestEntry);

                        // TODO: Verificar si ya existen el número minimo de instancias
                        // Crear instancias automáticamente según se vayan llenando las anteriores
                        if (bValid && contest.isFull()) {
                            TemplateContest.findOne(contest.templateContestId).instantiateContest(false);
                        }
                    }
                }
                else {
                    // Ya estaba en el contest
                    bValid = true;
                }
            }

            updateState(JobState.PROCESSING, bValid ? JobState.DONE : JobState.CANCELING);
        }

        if (state.equals(JobState.CANCELING)) {
            cancelAccountOp();

            updateState(JobState.CANCELING, JobState.CANCELED);
        }
    }

    @Override
    public void continueProcessing() {
        // Cancelamos si:

        // + No ha empezado a procesarse (JobState.TODO)
        // + Estaba en proceso de cancelarse (JobState.CANCELING)
        // + No se generó ningún contestEntry
        boolean cancel = (state.equals(JobState.TODO) || state.equals(JobState.CANCELING) || contestEntryId == null);

        if (!cancel) {
            // + No se insertó el contestEntry en el contest
            cancel = (Contest.findOneFromContestEntry(contestEntryId) == null);
        }

        if (cancel) {
            cancelAccountOp();
            updateState(state, JobState.CANCELED);
        }
        else {
            // Si ya está incluido en el contest, lo damos por terminado
            updateState(state, JobState.DONE);
        }
    }

    private boolean transactionPayment(int entryFee) {
        boolean transactionValid = false;

        // Registramos el seqId, de tal forma que si se produce una alteracion en el número de operaciones
        // del usuario se lance una excepcion que impida la inserción ya que (accountId, seqId) es "unique key"
        Integer seqId = User.getSeqId(userId) + 1;

        // El usuario tiene dinero suficiente?
        BigDecimal userBalance = User.calculateBalance(userId);
        if (userBalance.compareTo(new BigDecimal(entryFee)) >= 0) {
            try {
                // Registrar el pago
                AccountingOp accountingOp = AccountingOpEnterContest.create(contestId, contestEntryId, ImmutableList.of(
                        new AccountOp(userId, new BigDecimal(-entryFee), seqId)
                ));

                transactionValid = (accountingOp != null);
            }
            catch(DuplicateKeyException duplicateKeyException) {
                play.Logger.info("DuplicateKeyException");
            }
        }

        return transactionValid;
    }

    private boolean transactionContestEntryInsert(Contest contest, ContestEntry contestEntry) {
        // Insertamos el contestEntry en el contest
        //  Comprobamos que el contest siga ACTIVE, que el usuario no esté ya inscrito y que existan Huecos Libres
        String query = String.format("{_id: #, state: \"ACTIVE\", contestEntries.userId: {$ne: #}, contestEntries.%s: {$exists: false}}", contest.maxEntries - 1);
        WriteResult result = Model.contests().update(query, contestId, userId)
                .with("{$addToSet: {contestEntries: #}}", contestEntry);

        // Comprobamos el número de documentos afectados (error == 0)
        return (result.getN() > 0);
    }

    private void cancelAccountOp () {
        // Tenemos que tener un contestEntryId para proceder a la cancelación
        if (contestEntryId != null) {
            // Registramos el seqId, de tal forma que si se produce una alteracion en el número de operaciones
            // del usuario se lance una excepcion que impida la inserción ya que (accountId, seqId) es "unique key"
            Integer seqId = User.getSeqId(userId) + 1;

            // Hemos quitado dinero al usuario...?
            AccountingOp accountingOp = AccountingOpEnterContest.findOne(contestId, contestEntryId);
            if (accountingOp != null) {
                Contest contest = Contest.findOne(contestId);

                // Generamos la operación de cancelación (ingresarle dinero)
                AccountingOpCancelContestEntry.create(contestId, contestEntryId, ImmutableList.of(
                        new AccountOp(userId, new BigDecimal(contest.entryFee), seqId)
                ));
            }
        }
    }

    private ContestEntry createContestEntry() {
        ContestEntry contestEntry = null;

        // Generamos el identificador del ContestEntry
        contestEntryId = new ObjectId();

        // Actualizamos el job el identificador
        WriteResult result = Model.jobs().update(
            "{ _id: #}", jobId
        ).with(
            "{$set: { contestEntryId: # }}", contestEntryId
        );

        // Si va bien, creamos el contestEntry
        if (result.getN() > 0) {
            contestEntry = new ContestEntry(userId, soccerIds);
            contestEntry.contestEntryId = contestEntryId;
        }

        return contestEntry;
    }

    public static EnterContestJob create(ObjectId userId, ObjectId contestId, List<ObjectId> soccersIds) {
        EnterContestJob job = new EnterContestJob(userId, contestId, soccersIds);
        insert(JobType.ENTER_CONTEST, job);
        job.apply();
        return job;
    }
}