package controllers.admin;

import com.google.common.collect.ImmutableList;
import model.*;
import model.accounting.AccountOp;
import model.accounting.AccountingTranCancelContest;
import model.accounting.AccountingTranPrize;
import org.joda.money.CurrencyUnit;
import org.joda.money.Money;
import play.Logger;
import play.mvc.Controller;
import play.mvc.Result;
import utils.ReturnHelper;

import java.util.ArrayList;
import java.util.List;

public class ContestController extends Controller {
    public static Result index() {
        return ok(views.html.contest_list.render());
    }

    public static Result indexAjax() {
        return PaginationData.withAjax(request().queryString(), Model.contests(), Contest.class, new PaginationData() {
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
                    case 5: if(contest.isHistory()) {
                                return "Finished";
                            } else if(contest.isCanceled()) {
                                return "Canceled";
                            } else if(contest.isLive()) {
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
            else if (prizes.getValue(contestEntry.position).isGreaterThan(Money.zero(CurrencyUnit.EUR))) {
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
                else if (!accountOp.value.equals(contest.entryFee)) {
                    errors.add(String.format("contestEntry: %s AccountOp: %s != %s",
                            contestEntry.contestEntryId, accountOp.value, contest.entryFee));
                }
            }
        }

        return errors;
    }

}
