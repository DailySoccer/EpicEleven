package model.jobs;

import com.google.common.collect.ImmutableList;
import com.mongodb.DuplicateKeyException;
import com.mongodb.WriteResult;
import model.*;
import model.accounting.AccountOp;
import model.accounting.AccountingTran;
import model.accounting.AccountingTranCancelContestEntry;
import model.accounting.AccountingTranEnterContest;
import org.bson.types.ObjectId;
import org.joda.money.Money;
import utils.MoneyUtils;

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

                    bValid = contest.entryFee.isNegativeOrZero() || transactionPayment(contest.entryFee);

                    if (bValid) {
                        Contest contestModified = transactionInsertContestEntry(contest, contestEntry);
                        if (contestModified != null) {
                            bValid = true;

                            // Crear instancias automáticamente según se vayan llenando las anteriores
                            if (contestModified.isFull()) {
                                TemplateContest.maintainingMinimumNumberOfInstances(contest.templateContestId);
                            }
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

    private boolean transactionPayment(Money entryFee) {
        boolean transactionValid = false;

        // Registramos el seqId, de tal forma que si se produce una alteracion en el número de operaciones
        // del usuario se lance una excepcion que impida la inserción ya que (accountId, seqId) es "unique key"
        Integer seqId = User.getSeqId(userId) + 1;

        // El usuario tiene dinero suficiente?
        Money userBalance = User.calculateBalance(userId);
        if (MoneyUtils.compareTo(userBalance, entryFee) >= 0) {
            try {
                // Registrar el pago
                AccountingTran accountingTran = AccountingTranEnterContest.create(contestId, contestEntryId, ImmutableList.of(
                        new AccountOp(userId, entryFee.negated(), seqId)
                ));

                transactionValid = (accountingTran != null);
            }
            catch(DuplicateKeyException duplicateKeyException) {
                play.Logger.info("DuplicateKeyException");
            }
        }

        return transactionValid;
    }

    private Contest transactionInsertContestEntry(Contest contest, ContestEntry contestEntry) {
        // Insertamos el contestEntry en el contest
        //  Comprobamos que el contest siga ACTIVE, que el usuario no esté ya inscrito y que existan Huecos Libres
        String query = String.format("{_id: #, state: \"ACTIVE\", contestEntries.userId: {$ne: #}, contestEntries.%s: {$exists: false}}", contest.maxEntries - 1);
        return Model.contests().findAndModify(query, contestId, userId)
                .with("{$addToSet: {contestEntries: #}}", contestEntry)
                .returnNew()
                .as(Contest.class);
    }

    private void cancelAccountOp () {
        // Tenemos que tener un contestEntryId para proceder a la cancelación
        if (contestEntryId != null) {
            // Registramos el seqId, de tal forma que si se produce una alteracion en el número de operaciones
            // del usuario se lance una excepcion que impida la inserción ya que (accountId, seqId) es "unique key"
            Integer seqId = User.getSeqId(userId) + 1;

            // Hemos quitado dinero al usuario...?
            AccountingTran accountingTran = AccountingTranEnterContest.findOne(contestId, contestEntryId);
            if (accountingTran != null) {
                Contest contest = Contest.findOne(contestId);

                // Generamos la operación de cancelación (ingresarle dinero)
                AccountingTranCancelContestEntry.create(contestId, contestEntryId, ImmutableList.of(
                        new AccountOp(userId, contest.entryFee, seqId)
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