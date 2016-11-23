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
import play.Logger;
import utils.MoneyUtils;

import java.util.List;
import java.util.stream.Collectors;

public class EnterContestJob extends Job {
    public ObjectId userId;
    public ObjectId contestId;
    public String formation;
    public List<ObjectId> soccerIds;

    // ContestEntry generado por este Job
    public ObjectId contestEntryId;

    public EnterContestJob() {}

    private EnterContestJob(ObjectId userId, ObjectId contestId, String formation, List<ObjectId> soccersIds) {
        this.userId = userId;
        this.contestId = contestId;
        this.formation = formation;
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

                    // Será válido, si el contest es GRATIS o el usuario tiene dinero para realizar el contestEntry
                    bValid = contest.entryFee.isNegativeOrZero() || transactionPayment(contest, contestEntry);

                    if (bValid) {
                        boolean contestModified = transactionInsertContestEntry(contest, contestEntry);
                        if (contestModified) {
                            bValid = true;

                            // Añadimos el contestEntry al contest, a efectos del check "isFull"
                            contest.contestEntries.add(contestEntry);

                            // Crear instancias automáticamente según se vayan llenando las anteriores
                            // (salvo que sea un contest creado por un usuario)
                            if (!contest.isCreatedByUser() && contest.isFull()) {
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

    private boolean transactionPayment(Contest contest, ContestEntry contestEntry) {
        boolean transactionValid = false;

        Money entryFee = contest.entryFee;

        // Los contests que requieren energía, los gestionaremos sin necesidad de transacciones
        if (entryFee.getCurrencyUnit().equals(MoneyUtils.CURRENCY_ENERGY)) {
            // El usuario puede usar la energía necesaria?
            User user = User.findOne(userId);
            transactionValid = user.useEnergy(entryFee);
        }
        else {
            // Registramos el seqId, de tal forma que si se produce una alteracion en el número de operaciones
            // del usuario se lance una excepcion que impida la inserción ya que (accountId, seqId) es "unique key"
            Integer seqId = User.getSeqId(userId) + 1;

            Money moneyNeeded = entryFee;

            // En los torneos Oficiales, el usuario también tiene que pagar los futbolistas de nivel superior al suyo
            if (entryFee.getCurrencyUnit().equals(MoneyUtils.CURRENCY_GOLD)) {
                Money managerBalance = User.calculateManagerBalance(userId);

                // Modificamos el contestEntry con los players que han de ser comprados...
                List<InstanceSoccerPlayer> soccerPlayers = contest.getInstanceSoccerPlayers(soccerIds);
                List<InstanceSoccerPlayer> playersToBuy = User.playersToBuy(managerBalance, soccerPlayers);
                contestEntry.playersPurchased = playersToBuy.stream().map( instanceSoccerPlayer ->
                                instanceSoccerPlayer.templateSoccerPlayerId
                ).collect(Collectors.toList());

                moneyNeeded = moneyNeeded.plus(User.moneyToBuy(contest, managerBalance, playersToBuy));
            }

            // El usuario tiene dinero suficiente?
            if (User.hasMoney(userId, moneyNeeded)) {
                try {
                    // Registrar el pago
                    AccountingTran accountingTran = AccountingTranEnterContest.create(entryFee.getCurrencyUnit().getCode(), contestId, contestEntryId, ImmutableList.of(
                            new AccountOp(userId, moneyNeeded.negated(), seqId)
                    ));

                    transactionValid = (accountingTran != null);
                } catch (DuplicateKeyException duplicateKeyException) {
                    play.Logger.info("DuplicateKeyException");
                }
            }
        }

        return transactionValid;
    }

    private boolean transactionInsertContestEntry(Contest contest, ContestEntry contestEntry) {
        // Insertamos el contestEntry en el contest
        //  Comprobamos que el contest siga ACTIVE, que el usuario no esté ya inscrito y que existan Huecos Libres (maxEntries <= 0 : Ilimitado número de participantes)
        String query = String.format("{_id: #, state: \"ACTIVE\", contestEntries.userId: {$ne: #}, $or: [{maxEntries: {$lte: 0}}, {contestEntries.%s: {$exists: false}}]}", contest.maxEntries - 1);
        WriteResult result = Model.contests().update(query, contestId, userId)
                .with("{$push: {contestEntries: #}, $inc: {freeSlots : -1}}", contestEntry);

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
            AccountingTran accountingTran = AccountingTranEnterContest.findOne(contestId, contestEntryId);
            if (accountingTran != null) {
                Contest contest = Contest.findOne(contestId);

                // Generamos la operación de cancelación (ingresarle dinero)
                AccountingTranCancelContestEntry.create(contest.entryFee.getCurrencyUnit().getCode(), contestId, contestEntryId, ImmutableList.of(
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
            contestEntry = new ContestEntry(userId, formation, soccerIds);
            contestEntry.contestEntryId = contestEntryId;
        }

        return contestEntry;
    }

    public static EnterContestJob create(ObjectId userId, ObjectId contestId, String formation, List<ObjectId> soccersIds) {
        EnterContestJob job = new EnterContestJob(userId, contestId, formation, soccersIds);
        insert(JobType.ENTER_CONTEST, job);
        job.apply();
        return job;
    }
}