package controllers.admin;

import java.math.RoundingMode;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

import actions.CheckTargetEnvironment;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.mongodb.WriteConcern;
import model.*;
import org.bson.types.ObjectId;
import org.joda.money.Money;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import play.Logger;
import play.data.Form;
import play.mvc.Controller;
import play.mvc.Result;
import utils.MoneyUtils;
import utils.ReturnHelper;

import static play.data.Form.form;

public class TemplateContestController extends Controller {

    // Test purposes
    public static int templateCount = 0;
    public static final String[] contestNameSuffixes = {"All stars", "Special", "Regular", "Professional",
            "Just 4 Fun", "Weekly", "Experts Only", "Friends", "Pro Level", "Friday Only", "Amateur", "Weekend Only", "For Fame & Glory"};

    public static Result index() {
        return ok(views.html.template_contest_list.render(getCreatingTemplateContestsState()));
    }

    public static Result indexAjax() {
        return PaginationData.withAjax(request().queryString(), Model.templateContests(), TemplateContest.class, new PaginationData() {
            public List<String> getFieldNames() {
                return ImmutableList.of(
                        "state",
                        "name",
                        "",              // Num. Matches
                        "optaCompetitionId",
                        "minInstances",
                        "maxEntries",
                        "salaryCap",
                        "entryFee",
                        "prizeMultiplier",
                        "",             // Prize Pool
                        "prizeType",
                        "startDate",
                        "activationAt",
                        "",             // Edit
                        "",             // Delete
                        ""              // Simulation

                );
            }

            public String getFieldByIndex(Object data, Integer index) {
                TemplateContest templateContest = (TemplateContest) data;
                switch (index) {
                    case 0:
                        return templateContest.state.toString();
                    case 1:
                        return templateContest.name + ((templateContest.customizable) ? "**" : "");
                    case 2:
                        return String.valueOf(templateContest.templateMatchEventIds.size());
                    case 3:
                        return templateContest.optaCompetitionId;
                    case 4:
                        return String.valueOf(templateContest.minInstances);
                    case 5:
                        return String.valueOf(templateContest.maxEntries);
                    case 6:
                        return String.valueOf(templateContest.salaryCap);
                    case 7:
                        return MoneyUtils.asString(templateContest.entryFee);
                    case 8:
                        return String.valueOf(templateContest.prizeMultiplier);
                    case 9:
                        return MoneyUtils.asString(templateContest.getPrizePool());
                    case 10:
                        return String.valueOf(templateContest.prizeType);
                    case 11:
                        return GlobalDate.formatDate(templateContest.startDate);
                    case 12:
                        return GlobalDate.formatDate(templateContest.activationAt);
                    case 13:
                        return "";
                    case 14:
                        return "";
                    case 15:
                        return templateContest.simulation ? "Simulation" : "Real";
                }
                return "<invalid value>";
            }

            public String getRenderFieldByIndex(Object data, String fieldValue, Integer index) {
                TemplateContest templateContest = (TemplateContest) data;
                switch (index) {
                    case 0:
                        String classStyle = "btn btn-warning";
                        if (templateContest.state.isDraft())            classStyle = "btn btn-default";
                        else if (templateContest.state.isHistory())     classStyle = "btn btn-danger";
                        else if (templateContest.state.isLive())        classStyle = "btn btn-success";
                        else if (templateContest.state.isActive())      classStyle = "btn btn-warning";
                        return String.format("<a href=\"%s\"><button class=\"%s\">%s</button></a>",
                                routes.TemplateContestController.createClone(templateContest.templateContestId.toString()), classStyle, templateContest.state);
                    case 1:
                        return String.format("<a href=\"%s\" style=\"white-space: nowrap\">%s</a>",
                                routes.TemplateContestController.show(templateContest.templateContestId.toString()),
                                fieldValue);
                    case 13:
                        return (templateContest.state.isDraft() || templateContest.state.isOff() || templateContest.state.isActive())
                                ? String.format("<a href=\"%s\"><button class=\"btn btn-success\">Edit</button></a>",
                                routes.TemplateContestController.edit(templateContest.templateContestId.toString()))
                                : "";
                    case 14:
                        return templateContest.state.isDraft()
                                ? String.format("<a href=\"%s\"><button class=\"btn btn-success\">+</button></a> <a href=\"%s\"><button class=\"btn btn-danger\">-</button></a>",
                                routes.TemplateContestController.publish(templateContest.templateContestId.toString()),
                                routes.TemplateContestController.destroy(templateContest.templateContestId.toString()))
                                : templateContest.state.isOff() ?
                                String.format("<a href=\"%s\"><button class=\"btn btn-danger\">-</button></a>",
                                        routes.TemplateContestController.destroy(templateContest.templateContestId.toString()))
                                : "";
                    case 15:
                        return templateContest.simulation
                                ? String.format("<button class=\"btn btn-success\">Simulation</button>")
                                : "";
                }
                return fieldValue;
            }
        });
    }

    public static Result show(String templateContestId) {
        TemplateContest templateContest = TemplateContest.findOne(new ObjectId(templateContestId));
        return ok(views.html.template_contest.render(
                templateContest,
                templateContest.getTemplateMatchEvents(),
                TemplateSoccerTeam.findAllAsMap()));
    }

    public static Result newForm() {
        TemplateContestForm params = new TemplateContestForm();

        Form<TemplateContestForm> templateContestForm = Form.form(TemplateContestForm.class).fill(params);
        return ok(views.html.template_contest_add.render(templateContestForm, TemplateContestForm.matchEventsOptions(params.createdAt), false));
    }

    public static Result edit(String templateContestId) {
        TemplateContest templateContest = TemplateContest.findOne(new ObjectId(templateContestId));
        return edit(templateContest);
    }

    public static Result edit(TemplateContest templateContest) {
        TemplateContestForm params = new TemplateContestForm(templateContest);

        Form<TemplateContestForm> templateContestForm = Form.form(TemplateContestForm.class).fill(params);
        if (templateContest.state.isActive()) {
            List<TemplateMatchEvent> templateMatchEvents = templateContest.getTemplateMatchEvents();
            return ok(views.html.template_contest_add.render(templateContestForm, TemplateContestForm.matchEventsOptions(templateMatchEvents), templateContest.state.isActive()));
        }
        return ok(views.html.template_contest_add.render(templateContestForm, TemplateContestForm.matchEventsOptions(params.createdAt), templateContest.state.isActive()));
    }

    public static Result createClone(String templateContestId) {
        TemplateContest clonedTemplateContest = TemplateContest.findOne(new ObjectId(templateContestId)).copy();
        clonedTemplateContest.templateContestId = null;
        clonedTemplateContest.state = ContestState.DRAFT;
        clonedTemplateContest.name += " (clone)";
        return edit(clonedTemplateContest);
    }

    public static Result publish(String templateContestId) {
        TemplateContest.publish(new ObjectId(templateContestId));
        return redirect(routes.TemplateContestController.index());
    }

    public static Result destroy(String templateContestId) {
        TemplateContest templateContest = TemplateContest.findOne(new ObjectId(templateContestId));
        TemplateContest.remove(templateContest);
        return redirect(routes.TemplateContestController.index());
    }

    public static Result create() {
        Form<TemplateContestForm> templateContestForm = form(TemplateContestForm.class).bindFromRequest();
        if (templateContestForm.hasErrors()) {
            String createdAt = templateContestForm.field("createdAt").valueOr("0");
            return badRequest(views.html.template_contest_add.render(templateContestForm, TemplateContestForm.matchEventsOptions(Long.parseLong(createdAt)), false));
        }

        TemplateContestForm params = templateContestForm.get();

        boolean isNew = params.id.isEmpty();

        TemplateContest templateContest = new TemplateContest();

        templateContest.templateContestId = !isNew ? new ObjectId(params.id) : null;
        templateContest.state = params.state;
        templateContest.customizable = params.typeCustomizable.equals(TemplateContestForm.SelectionYESNO.YES);
        templateContest.simulation = (params.typeContest == TypeContest.VIRTUAL);
        templateContest.name = params.name;
        templateContest.minInstances = params.minInstances;
        templateContest.maxEntries = params.maxEntries;
        templateContest.salaryCap = params.salaryCap.money;

        // En la simulación usaremos ENERGY, en los reales GOLD
        templateContest.entryFee = Money.zero(templateContest.simulation ? MoneyUtils.CURRENCY_ENERGY : MoneyUtils.CURRENCY_GOLD).plus(params.entryFee);
        templateContest.prizeType = params.prizeType;
        templateContest.prizeMultiplier = templateContest.simulation ? params.prizeMultiplier : Prizes.PRIZE_MULTIPLIER_FOR_REAL_CONTEST;

        templateContest.specialImage = params.specialImage;

        templateContest.activationAt = new DateTime(params.activationAt).withZone(DateTimeZone.UTC).toDate();
        templateContest.createdAt = new Date(params.createdAt);

        Date startDate = null;
        templateContest.templateMatchEventIds = new ArrayList<>();
        for (String templateMatchEventId : params.templateMatchEvents) {
            TemplateMatchEvent templateMatchEvent = TemplateMatchEvent.findOne(new ObjectId(templateMatchEventId));
            templateContest.optaCompetitionId = templateMatchEvent.optaCompetitionId;
            templateContest.templateMatchEventIds.add(templateMatchEvent.templateMatchEventId);

            if (startDate == null || templateMatchEvent.startDate.before(startDate)) {
                startDate = templateMatchEvent.startDate;
            }
        }

        // Si es una "simulación" la fecha de comienzo la leeremos del formulario
        if (templateContest.simulation) {
            templateContest.startDate = new DateTime(params.startDate).withZone(DateTimeZone.UTC).toDate();
        }
        else {
            // Si no es simulación, la fecha de comienzo es la del primer partido
            templateContest.startDate = startDate;
        }

        if (isNew) {
            Model.templateContests().insert(templateContest);
            OpsLog.onNew(templateContest);
        }
        else {
            Model.templateContests().update("{_id: #}", templateContest.templateContestId).with(templateContest);
            updateActiveContestsFromTemplate(templateContest);
            OpsLog.onChange(templateContest);
        }

        return redirect(routes.TemplateContestController.index());
    }

    private static void updateActiveContestsFromTemplate(TemplateContest templateContest) {
        // Only name can change in Active contests
        Model.contests().withWriteConcern(WriteConcern.SAFE)
                .update("{templateContestId: #}", templateContest.getId())
                .multi()
                .with("{$set: {name: #, specialImage: #}}", templateContest.name, templateContest.specialImage);
    }

    @CheckTargetEnvironment
    public static Result createAll() {
        templateCount = 0;
        Iterable<TemplateMatchEvent> matchEventResults = Model.templateMatchEvents().find().sort("{startDate: 1}").as(TemplateMatchEvent.class);

        DateTime dateTime = null;
        List<TemplateMatchEvent> matchEvents = new ArrayList<>();   // Partidos que juntaremos en el mismo contests
        for (TemplateMatchEvent match: matchEventResults) {
            DateTime matchDateTime = new DateTime(match.startDate, DateTimeZone.UTC);
            if (dateTime == null) {
                dateTime = matchDateTime;
            }

            // El partido es de un dia distinto?
            if (dateTime.dayOfYear().get() != matchDateTime.dayOfYear().get()) {
                // Logger.info("{} != {}", dateTime.dayOfYear().get(), matchDateTime.dayOfYear().get());

                // El dia anterior tenia un numero suficiente de partidos? (minimo 2)
                if (matchEvents.size() >= 2) {

                    // crear el contest
                    createMock(matchEvents);

                    // empezar a registrar los partidos del nuevo contest
                    matchEvents.clear();
                }
            }

            dateTime = matchDateTime;
            matchEvents.add(match);
        }

        // Tenemos partidos sin incluir en un contest?
        if (matchEvents.size() > 0) {
            createMock(matchEvents);
        }

        return redirect(routes.TemplateContestController.index());
    }

    public static Result setCreatingTemplateContestsState(boolean state) {
        Model.actors().tell("ContestsActor", state ? "StartCreatingTemplateContests" : "StopCreatingTemplateContests");
        return redirect(routes.TemplateContestController.index());
    }

    public static void createMock(List<TemplateMatchEvent> templateMatchEvents) {
        createMock(templateMatchEvents, MoneyUtils.zero, 3, PrizeType.FREE, SalaryCap.EASY);
        //createMock(templateMatchEvents, 0, 5, PrizeType.FREE);
        //createMock(templateMatchEvents, 0, 10, PrizeType.FREE);
        createMock(templateMatchEvents, MoneyUtils.zero, 25, PrizeType.FREE, SalaryCap.STANDARD);


        for (int i = 1; i<=6; i++) {
            Money money = Money.of(MoneyUtils.CURRENCY_GOLD, i);

            switch (i) {
                case 1:
                    createMock(templateMatchEvents, money, 2, PrizeType.WINNER_TAKES_ALL, SalaryCap.DIFFICULT); //DIFICIL
                    createMock(templateMatchEvents, money, 10, PrizeType.TOP_3_GET_PRIZES, SalaryCap.STANDARD); //MEDIO
                    break;
                case 2:
                    createMock(templateMatchEvents, money, 25, PrizeType.WINNER_TAKES_ALL, SalaryCap.STANDARD); //MEDIO
                    break;
                case 3:
                    createMock(templateMatchEvents, money, 5, PrizeType.TOP_THIRD_GET_PRIZES, SalaryCap.EASY); //FACIL
                    createMock(templateMatchEvents, money, 3, PrizeType.FIFTY_FIFTY, SalaryCap.DIFFICULT); //DIFICIL
                    break;
                case 4:
                    createMock(templateMatchEvents, money, 3, PrizeType.FIFTY_FIFTY, SalaryCap.STANDARD); //MEDIO
                    createMock(templateMatchEvents, money, 10, PrizeType.TOP_3_GET_PRIZES, SalaryCap.EASY); //FACIL
                    break;
                case 5:
                    createMock(templateMatchEvents, money, 10, PrizeType.TOP_3_GET_PRIZES, SalaryCap.DIFFICULT); //DIFICIL
                    createMock(templateMatchEvents, money, 25, PrizeType.WINNER_TAKES_ALL, SalaryCap.STANDARD); //MEDIO
                    break;
                case 6:
                    createMock(templateMatchEvents, money, 25, PrizeType.WINNER_TAKES_ALL, SalaryCap.EASY); //FACIL
                    break;
            }
            /*
            createMock(templateMatchEvents, i, 2, PrizeType.WINNER_TAKES_ALL);
            //createMock(templateMatchEvents, i, 3, PrizeType.WINNER_TAKES_ALL);
            //createMock(templateMatchEvents, i, 5, PrizeType.WINNER_TAKES_ALL);
            //createMock(templateMatchEvents, i, 10, PrizeType.WINNER_TAKES_ALL);
            createMock(templateMatchEvents, i, 25, PrizeType.WINNER_TAKES_ALL);

            //createMock(templateMatchEvents, i, 3, PrizeType.TOP_3_GET_PRIZES);
            //createMock(templateMatchEvents, i, 5, PrizeType.TOP_3_GET_PRIZES);
            createMock(templateMatchEvents, i, 10, PrizeType.TOP_3_GET_PRIZES);
            //createMock(templateMatchEvents, i, 25, PrizeType.TOP_3_GET_PRIZES);

            //createMock(templateMatchEvents, i, 3, PrizeType.TOP_THIRD_GET_PRIZES);
            createMock(templateMatchEvents, i, 5, PrizeType.TOP_THIRD_GET_PRIZES);
            //createMock(templateMatchEvents, i, 10, PrizeType.TOP_THIRD_GET_PRIZES);
            //createMock(templateMatchEvents, i, 25, PrizeType.TOP_THIRD_GET_PRIZES);

            createMock(templateMatchEvents, i, 3, PrizeType.FIFTY_FIFTY);
            //createMock(templateMatchEvents, i, 5, PrizeType.FIFTY_FIFTY);
            //createMock(templateMatchEvents, i, 10, PrizeType.FIFTY_FIFTY);
            //createMock(templateMatchEvents, i, 25, PrizeType.FIFTY_FIFTY);*/
        }
    }

    private static void createMock(List<TemplateMatchEvent> templateMatchEvents, Money entryFee, int maxEntries, PrizeType prizeType, SalaryCap salaryCap) {
        if (templateMatchEvents.size() == 0) {
            Logger.error("create: templateMatchEvents is empty");
            return;
        }

        Date startDate = templateMatchEvents.get(0).startDate;

        TemplateContest templateContest = new TemplateContest();

        templateCount = (templateCount + 1) % contestNameSuffixes.length;

        templateContest.name = "%StartDate - " + contestNameSuffixes[templateCount] + " " + TemplateContest.FILL_WITH_MOCK_USERS;
        templateContest.minInstances = 3;
        templateContest.maxEntries = maxEntries;
        templateContest.prizeType = prizeType;
        templateContest.entryFee = entryFee;
        templateContest.salaryCap = salaryCap.money;
        templateContest.startDate = startDate;
        templateContest.templateMatchEventIds = new ArrayList<>();

        // Se activará 2 dias antes a la fecha del partido
        templateContest.activationAt = new DateTime(startDate).minusDays(2).toDate();

        templateContest.createdAt = GlobalDate.getCurrentDate();

        for (TemplateMatchEvent match: templateMatchEvents) {
            templateContest.optaCompetitionId = match.optaCompetitionId;
            templateContest.templateMatchEventIds.add(match.templateMatchEventId);
        }

        // Logger.info("MockData: Template Contest: {} ({})", templateContest.templateMatchEventIds, GlobalDate.formatDate(startDate));

        Model.templateContests().insert(templateContest);
    }

    public static Result getPrizes(String prizeType, Integer maxEntries, Integer prizePool) {
        Prizes prizes = Prizes.findOne(PrizeType.valueOf(prizeType), maxEntries, Money.of(MoneyUtils.CURRENCY_DEFAULT, prizePool));
        return new ReturnHelper(prizes.getAllValues()).toResult();
    }

    static private Boolean getCreatingTemplateContestsState() {
        Boolean response = Model.actors().tellAndAwait("ContestsActor", "GetCreatingTemplateContestsState");
        if (response != null) {
            _creatingTemplateContestState.set(response);
        }
        return _creatingTemplateContestState.get();
    }

    static private AtomicBoolean _creatingTemplateContestState = new AtomicBoolean();
}
