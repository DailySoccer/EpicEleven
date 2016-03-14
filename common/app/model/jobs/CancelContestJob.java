package model.jobs;

import com.mongodb.WriteResult;
import model.*;
import model.accounting.AccountOp;
import model.accounting.AccountingTran;
import model.accounting.AccountingTranCancelContest;
import model.shop.Order;
import org.bson.types.ObjectId;
import org.joda.money.CurrencyUnit;
import org.joda.money.Money;
import play.Logger;
import utils.MoneyUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

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
                            .findAndModify("{_id: #, state: { $ne: \"CANCELED\" }, $where: \"this.contestEntries.length < this.minEntries\"}", contestId)
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
                    if (MoneyUtils.isGreaterThan(contest.entryFee, MoneyUtils.zero) && !contest.contestEntries.isEmpty()) {
                        if (contest.entryFee.getCurrencyUnit().equals(MoneyUtils.CURRENCY_GOLD)) {
                            List<AccountOp> accounts = new ArrayList<>();
                            for (ContestEntry contestEntry : contest.contestEntries) {
                                // No devolvemos el dinero del que crea el Contest
                                if (contest.authorId != null && contest.authorId.equals(contestEntry.userId)) {
                                    Logger.debug("Contest Cancelado: {} No devolvemos el dinero al authorId: {}", contest.contestId.toString(), contest.authorId.toString());
                                    continue;
                                }

                                Money money = AccountingTran.moneySpentOnContest(contestEntry.userId, contestId);
                                // Queremos devolver el dinero que se gastó al entrar en el contest
                                money = money.negated();

                                // Queremos devolver el dinero adicional que se gastó en comprar futbolistas
                                Money moneySpent = Order.moneySpentOnContest(contestEntry.userId, contestId);
                                if (moneySpent.isPositive()) {
                                    money = money.plus(moneySpent);
                                }

                                accounts.add(new AccountOp(contestEntry.userId, money, User.getSeqId(contestEntry.userId) + 1));
                            }
                            if (accounts.size() > 0) {
                                AccountingTran accountingTran = AccountingTranCancelContest.create(contest.entryFee.getCurrencyUnit().getCode(), contestId, accounts);
                                bValid = (accountingTran != null);
                            }
                        }
                        else if (contest.entryFee.getCurrencyUnit().equals(MoneyUtils.CURRENCY_ENERGY)) {
                            Logger.debug("Cancel Contest: {} Devolviendo Energy: {}", contest.contestId.toString(), contest.entryFee.toString());
                            for (ContestEntry contestEntry : contest.contestEntries) {
                                // No devolvemos el dinero del que crea el Contest
                                if (contest.authorId != null && contest.authorId.equals(contestEntry.userId)) {
                                    Logger.debug("Contest Cancelado: {} No devolvemos el dinero al authorId: {}", contest.contestId.toString(), contest.authorId.toString());
                                    continue;
                                }

                                User user = User.findOne(contestEntry.userId);
                                user.addEnergy(contest.entryFee);
                                Logger.debug("*** User: {} Devolviendo Energy: {}", user.userId.toString(), contest.entryFee.toString());
                            }
                        }
                    }

                    // Enviamos avisos de cancelación
                    UserNotification.contestCancelled(contest).sendTo(contest.contestEntries.stream().map(contestEntry -> contestEntry.userId).collect(Collectors.toList()));

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
