package controllers.admin;

import com.google.common.collect.ImmutableList;
import model.*;
import model.accounting.*;
import org.joda.money.Money;
import play.Logger;
import play.mvc.Controller;
import play.mvc.Result;
import utils.MoneyUtils;
import utils.ReturnHelper;

import java.util.ArrayList;
import java.util.List;

public class ContestController extends Controller {
    public static Result index() {
        return ok(views.html.contest_list.render());
    }

    public static Result indexAjax() {
        return PaginationData.withAjax(request().queryString(), Model.contests(), Contest.class, new PaginationData() {
            public String projection() {
                return "{name: 1, 'contestEntries.userId': 1, maxEntries: 1, templateContestId: 1, optaCompetitionId: 1, state: 1}";
            }

            public List<String> getFieldNames() {
                return ImmutableList.of(
                    "name",
                    "",                     // contestEntries.size
                    "maxEntries",
                    "templateContestId",
                    "optaCompetitionId",
                    ""                      // templateContest.state
                );
            }

            public String getFieldByIndex(Object data, Integer index) {
                Contest contest = (Contest) data;
                switch (index) {
                    case 0: return contest.name;
                    case 1: return String.valueOf(contest.contestEntries.size());
                    case 2: return String.valueOf(contest.maxEntries);
                    case 3: return contest.templateContestId.toString();
                    case 4: return contest.optaCompetitionId;
                    case 5: if(contest.state.isHistory()) {
                                return "Finished";
                            } else if(contest.state.isCanceled()) {
                                return "Canceled";
                            } else if(contest.state.isLive()) {
                                return "Live";
                            } else {
                                return "Waiting";
                            }
                }
                return "<invalid value>";
            }

            public String getRenderFieldByIndex(Object data, String fieldValue, Integer index) {
                Contest contest = (Contest) data;
                switch (index) {
                    case 3: return String.format("<a href=\"%s\">%s</a>",
                                routes.TemplateContestController.show(fieldValue).url(),
                                fieldValue);
                    case 5:
                        if(fieldValue.equals("Finished")) {
                            return "<button class=\"btn btn-danger\">Finished</button>";
                        } else if(fieldValue.equals("Canceled")) {
                            return "<button class=\"btn btn-danger\">Canceled</button>";
                        } else if(fieldValue.equals("Live")) {
                            return "<button class=\"btn btn-success\">Live</button>";
                        } else {
                            return "<button class=\"btn btn-warning\">Waiting</button>";
                        }
                }
                return fieldValue;
            }
        });
    }

    public static Result show(String contestId) {
        return ok(views.html.contest.render(Contest.findOne(contestId)));
    }


    static public Result verifyPrizes() {
        boolean ret = true;

        Logger.info("verifyPrizes BEGIN");

        List<String> errors = new ArrayList<>();

        for (Contest contest : Contest.findAllHistoryClosed()) {
            List<String> errorsInPrize = errorsInPrize(contest);
            if (!errorsInPrize.isEmpty()) {
                String error = String.format("Prize: contest: %s error: %s", contest.contestId, errorsInPrize);
                errors.add(error);
                Logger.error(error);
            }
        }

        for (Contest contest : Contest.findAllCanceled()) {
            List<String> errorsInCanceledPrize = errorsInCanceledPrize(contest);
            if (!errorsInCanceledPrize.isEmpty()) {
                String error = String.format("CanceledPrize: contest: %s error: %s", contest.contestId, errorsInCanceledPrize);
                errors.add(error);
                Logger.error(error);
            }
        }

        Logger.info("verifyPrizes END");

        return errors.isEmpty() ? ok("OK") : new ReturnHelper(false, errors).toResult();
    }

    static public Result verifyEntryFee() {
        boolean ret = true;

        Logger.info("verifyEntryFee BEGIN");

        List<String> errors = new ArrayList<>();

        for (Contest contest : Contest.findAllHistoryClosed()) {
            List<String> errorsInPBonus = errorsInEntryFee(contest);
            if (!errorsInPBonus.isEmpty()) {
                String error = String.format("Bonus: contest: %s error: %s", contest.contestId, errorsInPBonus);
                errors.add(error);
                Logger.error(error);
            }
        }

        Logger.info("verifyEntryFee END");

        return errors.isEmpty() ? ok("OK") : new ReturnHelper(false, errors).toResult();
    }

    static private List<String> errorsInPrize(Contest contest) {
        List<String> errors = new ArrayList<>();

        if (contest.prizeType.equals(PrizeType.FREE)) {
            return errors;
        }

        Prizes prizes = Prizes.findOne(contest);
        for (ContestEntry contestEntry : contest.contestEntries) {
            // El contestEntry tendría que tener una posición de ranking válida
            if (contestEntry.position == -1) {
                errors.add(String.format("contestEntry: %s position: -1",
                        contestEntry.contestEntryId));
            }
            // Tendría que haber recibido el premio adecuado
            else if (!contestEntry.prize.equals(prizes.getValue(contestEntry.position))) {
                errors.add(String.format("contestEntry: %s prize %s != %s",
                        contestEntry.contestEntryId, contestEntry.prize.toString(), prizes.getValue(contestEntry.position)));
            }
            else if (prizes.getValue(contestEntry.position).isGreaterThan(MoneyUtils.zero)) {
                AccountingTranPrize tranPrize = AccountingTranPrize.findOne(contest.contestId);
                // Tendría que existir una transacción
                if (tranPrize == null) {
                    errors.add("Sin AccountingTranPrize");
                }
                else {
                    AccountOp accountOp = tranPrize.getAccountOp(contestEntry.userId);
                    // Tendría que tener una entrada entre las operaciones de la transacción
                    if (accountOp == null) {
                        errors.add(String.format("contestEntry: %s: Sin AccountOp", contestEntry.contestEntryId));
                    }
                    // Tendría que recibir el premio correspondiente
                    else if (!accountOp.value.equals(prizes.getValue(contestEntry.position))) {
                        errors.add(String.format("contestEntry: %s AccountOp: %s != %s",
                                contestEntry.contestEntryId, accountOp.value, prizes.getValue(contestEntry.position)));
                    }
                }
            }
        }

        return errors;
    }

    static private List<String> errorsInEntryFee(Contest contest) {
        List<String> errors = new ArrayList<>();

        // Si el contest tenía un entryFee
        if (contest.entryFee.isPositive()) {
            for (ContestEntry contestEntry : contest.contestEntries) {
                // Tendría que existir una transacción con el pago del entry
                AccountingTranEnterContest enterContestTransaction = AccountingTranEnterContest.findOne(contest.contestId, contestEntry.contestEntryId);
                if (enterContestTransaction != null) {
                    // Comprobamos si el usuario tenía algún bonus pendiente
                    Money bonusPending = User.calculateBonus(contestEntry.userId, contest.finishedAt);
                    if (bonusPending.isPositive()) {
                        // Tendría que existir una transacción convirtiendo "bonus to cash"
                        String bonusId = AccountingTranBonus.bonusToCashId(contest.contestId, contestEntry.userId);
                        AccountingTranBonus bonusTransaction = AccountingTranBonus.findOne(AccountingTran.TransactionType.BONUS_TO_CASH, bonusId);
                        if (bonusTransaction != null) {
                            // Comprobar que sea la misma cantidad
                            if (!MoneyUtils.equals(bonusTransaction.bonus.negated(), bonusTransaction.accountOps.get(0).value)) {
                                errors.add(String.format("entryFee %s: contest: %s contestEntry: %s: Bonus: %s != Cash %s",
                                        contest.entryFee.toString(), contest.contestId, contestEntry.contestEntryId,
                                        bonusTransaction.bonus.negated(), bonusTransaction.accountOps.get(0).value));
                            }
                        }
                        else {
                            errors.add(String.format("entryFee %s: contest: %s contestEntry: %s: bonusPending %s: Sin 'Bonus to Cash'",
                                    contest.entryFee, contest.contestId, contestEntry.contestEntryId, bonusPending));
                        }
                    }
                }
                else {
                    errors.add(String.format("entryFee %s: contest: %s contestEntry: %s: Sin transaccion",
                            contest.entryFee.toString(), contest.contestId, contestEntry.contestEntryId));
                }
            }
        }

        return errors;
    }

    static private List<String> errorsInCanceledPrize(Contest contest) {
        List<String> errors = new ArrayList<>();

        // Si el contest era gratuito o estaba vacio, OK
        if (contest.prizeType.equals(PrizeType.FREE) || contest.contestEntries.isEmpty()) {
            return errors;
        }

        if (contest.contestEntries.size() == contest.maxEntries) {
            Logger.warn("CanceledPrize: {} LLENO !!!", contest.contestId);
        }

        AccountingTranCancelContest tranCancel = AccountingTranCancelContest.findOne(contest.contestId);
        // Tendría que existir una transacción
        if (tranCancel == null) {
            errors.add("Sin AccountingTranCancelContest");
        }
        else {
            for (ContestEntry contestEntry : contest.contestEntries) {
                AccountOp accountOp = tranCancel.getAccountOp(contestEntry.userId);
                // Tendría que tener una entrada entre las operaciones de la transacción
                if (accountOp == null) {
                    errors.add(String.format("contestEntry: %s: Sin AccountOp", contestEntry.contestEntryId));
                }
                // Tendrían que devolverle el entryFee
                else if (!MoneyUtils.equals(accountOp.value, contest.entryFee)) {
                    errors.add(String.format("contestEntry: %s AccountOp: %s != %s",
                            contestEntry.contestEntryId, accountOp.value, contest.entryFee));
                }
            }
        }

        return errors;
    }

}
