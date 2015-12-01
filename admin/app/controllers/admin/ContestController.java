package controllers.admin;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import model.*;
import model.accounting.*;
import model.opta.OptaEventType;
import org.joda.money.Money;
import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormat;
import org.bson.types.ObjectId;
import org.joda.time.format.DateTimeFormatter;
import play.Logger;
import play.Play;
import play.mvc.Controller;
import play.mvc.Http;
import play.mvc.Result;
import utils.FileUtils;
import utils.MoneyUtils;
import utils.ReturnHelper;

import java.io.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ContestController extends Controller {
    static final String SEPARATOR_CSV = ";";
    static final DateTimeFormatter dateTimeFormatter = DateTimeFormat.forPattern("yyyy/MM/dd HH:mm");

    public static Result index() {
        return ok(views.html.contest_list.render());
    }

    public static Result indexAjax() {
        return PaginationData.withAjax(request().queryString(), Model.contests(), Contest.class, new PaginationData() {
            public String projection() {
                return "{name: 1, 'contestEntries.userId': 1, maxEntries: 1, entryFee: 1, prizeMultiplier: 1, templateContestId: 1, optaCompetitionId: 1, state: 1, simulation: 1}";
            }

            public List<String> getFieldNames() {
                return ImmutableList.of(
                    "name",
                    "",                     // contestEntries.size
                    "maxEntries",
                    "",                     // prize Pool
                    "templateContestId",
                    "optaCompetitionId",
                    "",                     // templateContest.state
                    ""                      // Simulation
                );
            }

            public String getFieldByIndex(Object data, Integer index) {
                Contest contest = (Contest) data;
                switch (index) {
                    case 0:
                        return contest.name;
                    case 1:
                        return String.valueOf(contest.contestEntries.size());
                    case 2:
                        return String.valueOf(contest.maxEntries);
                    case 3:
                        return MoneyUtils.asString(contest.getPrizePool());
                    case 4:
                        return contest.templateContestId.toString();
                    case 5:
                        return contest.optaCompetitionId;
                    case 6:
                            if (contest.state.isOff()) {
                                return "Off";
                            } else if(contest.state.isHistory()) {
                                return "Finished";
                            } else if(contest.state.isCanceled()) {
                                return "Canceled";
                            } else if(contest.state.isLive()) {
                                return "Live";
                            } else {
                                return "Waiting";
                            }
                    case 7: return "";
                }
                return "<invalid value>";
            }

            public String getRenderFieldByIndex(Object data, String fieldValue, Integer index) {
                Contest contest = (Contest) data;
                switch (index) {
                    case 4: return String.format("<a href=\"%s\">%s</a>",
                                routes.TemplateContestController.show(fieldValue).url(),
                                fieldValue);
                    case 6:
                        if(fieldValue.equals("Off")) {
                            return "<button class=\"btn btn-warning disabled\">Off</button>";
                        } else if(fieldValue.equals("Finished")) {
                            return "<button class=\"btn btn-danger\">Finished</button>";
                        } else if(fieldValue.equals("Canceled")) {
                            return "<button class=\"btn btn-danger\">Canceled</button>";
                        } else if(fieldValue.equals("Live")) {
                            return "<button class=\"btn btn-success\">Live</button>";
                        } else {
                            return "<button class=\"btn btn-warning\">Waiting</button>";
                        }
                    case 7:
                        return contest.simulation
                                ? "<button class=\"btn btn-success\">Simulation</button>"
                                : "";
                }
                return fieldValue;
            }
        });
    }

    public static Result show(String contestId) {
        return ok(views.html.contest.render(Contest.findOne(contestId)));
    }

    public enum FieldCSV {
        ID(0),
        SIMULATION(1),
        NAME(2),
        MAX_ENTRIES(3),
        SALARY_CAP(4),
        ENTRY_FEE(5),
        PRIZE_TYPE(6),
        PRIZE_MULTIPLIER(7),
        START_DATE(8),
        ACTIVATION_AT(9),
        SPECIAL_IMAGE(10);

        public final int id;

        FieldCSV(int id) {
            this.id = id;
        }
    }

    public static Result defaultCSV() {
        List<String> headers = new ArrayList<>();
        for (FieldCSV fieldCSV : FieldCSV.values()) {
            headers.add(fieldCSV.toString());
        }

        List<String> body = new ArrayList<>();

        List<TemplateContest> templateContest = TemplateContest.findAllDraft();

        templateContest.forEach( template -> {
            body.add(template.templateContestId.toString());
            body.add(String.valueOf(template.simulation));
            body.add(template.name);
            body.add(String.valueOf(template.maxEntries));
            body.add(String.valueOf(template.salaryCap));
            body.add(template.entryFee.toString());
            body.add(template.prizeType.toString());
            body.add(String.valueOf(template.prizeMultiplier));
            body.add(new DateTime(template.startDate).toString(dateTimeFormatter.withZoneUTC()));
            body.add(new DateTime(template.activationAt).toString(dateTimeFormatter.withZoneUTC()));
            body.add(template.specialImage);
        });

        String fileName = String.format("templateContests.csv");
        FileUtils.generateCsv(fileName, headers, body, SEPARATOR_CSV);

        FlashMessage.info(fileName);

        return redirect(routes.ContestController.index());
    }

    public static Result importFromCSV() {
        Http.MultipartFormData body = request().body().asMultipartFormData();
        Http.MultipartFormData.FilePart httpFile = body.getFile("csv");
        if (httpFile != null && importFromFileCSV(httpFile.getFile())) {
            FlashMessage.success("CSV read successfully");
            return redirect(routes.ContestController.index());
        } else {
            FlashMessage.danger("Missing file, select one through \"Choose file\"");
            return redirect(routes.ContestController.index());
        }
    }

    public static boolean importFromFileCSV(File file) {
        try {
            String line = "";

            BufferedReader br = new BufferedReader(new FileReader(file));
            while ((line = br.readLine()) != null) {

                String[] params = line.split(SEPARATOR_CSV);

                List<String> data = Arrays.asList(params);
                System.out.println(String.format("[%s]", Joiner.on(SEPARATOR_CSV).join(data)));

                // Cabecera?
                if (params[FieldCSV.ID.id].equals(FieldCSV.ID.toString())) {
                    continue;
                }

                ObjectId templateContestId = new ObjectId(params[FieldCSV.ID.id]);
                TemplateContest templateContest = TemplateContest.findOne(templateContestId);

                Contest contest = new Contest();
                contest.setupFromTemplateContest(templateContest);

                contest.templateContestId = templateContestId;
                contest.simulation = Boolean.valueOf(params[FieldCSV.SIMULATION.id]);
                contest.name = params[FieldCSV.NAME.id];
                contest.maxEntries = Integer.valueOf(params[FieldCSV.MAX_ENTRIES.id]);
                contest.salaryCap = Integer.valueOf(params[FieldCSV.SALARY_CAP.id]);
                contest.entryFee = Money.parse(params[FieldCSV.ENTRY_FEE.id]);
                contest.prizeType = PrizeType.valueOf(params[FieldCSV.PRIZE_TYPE.id]);
                contest.prizeMultiplier = Float.valueOf(params[FieldCSV.PRIZE_MULTIPLIER.id]);
                contest.startDate = DateTime.parse(params[FieldCSV.START_DATE.id], dateTimeFormatter.withZoneUTC()).toDate();
                contest.activationAt = DateTime.parse(params[FieldCSV.ACTIVATION_AT.id], dateTimeFormatter.withZoneUTC()).toDate();

                if (params.length > FieldCSV.SPECIAL_IMAGE.id)
                    contest.specialImage = params[FieldCSV.SPECIAL_IMAGE.id];

                contest.state = ContestState.OFF;
                contest.insert();
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }

        return true;
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
                    else if (!accountOp.asMoney().equals(prizes.getValue(contestEntry.position))) {
                        errors.add(String.format("contestEntry: %s AccountOp: %s != %s",
                                contestEntry.contestEntryId, accountOp.asMoney(), prizes.getValue(contestEntry.position)));
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
                if (enterContestTransaction == null) {
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
                else if (!MoneyUtils.equals(accountOp.asMoney(), contest.entryFee)) {
                    errors.add(String.format("contestEntry: %s AccountOp: %s != %s",
                            contestEntry.contestEntryId, accountOp.value, contest.entryFee));
                }
            }
        }

        return errors;
    }

}
