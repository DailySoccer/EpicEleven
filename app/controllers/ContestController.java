package controllers;

import actions.AllowCors;
import actions.UserAuthenticated;
import com.google.common.collect.ImmutableMap;
import model.*;
import org.bson.types.ObjectId;
import org.joda.money.Money;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import play.Logger;
import play.cache.Cache;
import play.cache.Cached;
import play.data.Form;
import play.data.validation.Constraints;
import play.mvc.Controller;
import play.mvc.Result;
import play.mvc.With;
import utils.*;

import java.util.*;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;

import static play.data.Form.form;

@AllowCors.Origin
public class ContestController extends Controller {

    private final static int CACHE_ACTIVE_CONTESTS = 60;
    private final static int CACHE_ACTIVE_CONTEST = 60;
    private final static int CACHE_VIEW_LIVE_CONTESTS = 60;
    private final static int CACHE_VIEW_HISTORY_CONTEST = 60 * 60 * 8;      // 8 horas
    private final static int CACHE_LIVE_MATCHEVENTS = 30;
    private final static int CACHE_LIVE_CONTESTENTRIES = 30;
    private final static int CACHE_CONTEST_INFO = 60;
    private final static int CACHE_EXISTS_LIVE = 60 * 5;         // 5 minutos

    private static final String ERROR_VIEW_CONTEST_INVALID = "ERROR_VIEW_CONTEST_INVALID";
    private static final String ERROR_MY_CONTEST_INVALID = "ERROR_MY_CONTEST_INVALID";
    private static final String ERROR_MY_CONTEST_ENTRY_INVALID = "ERROR_MY_CONTEST_ENTRY_INVALID";
    private static final String ERROR_TEMPLATE_CONTEST_INVALID = "ERROR_TEMPLATE_CONTEST_INVALID";
    private static final String ERROR_CONTEST_INVALID = "ERROR_CONTEST_INVALID";
    private static final String ERROR_TEMPLATE_CONTEST_NOT_ACTIVE = "ERROR_TEMPLATE_CONTEST_NOT_ACTIVE";
    private static final String ERROR_OP_UNAUTHORIZED = "ERROR_OP_UNAUTHORIZED";

    /*
        Parámetros para la creación de un contest por parte de un usuario.

        Se requiere un fantasyTeam correspondiente al contestEntry del usuario.
     */
    public static class CreateContestParams {
        @Constraints.Required
        public String templateContestId;

        @Constraints.Required
        public String name;

        @Constraints.Required
        public long millisecondsSinceEpoch;

        @Constraints.Required
        public boolean simulation;

        /*
        @Constraints.Required
        public String contestType;
        */

        @Constraints.Required
        public Integer maxEntries;

        @Constraints.Required
        public String soccerTeam;   // JSON con la lista de futbolistas seleccionados
    }

    /**
     * Create un contest por parte del usuario
     */
    @UserAuthenticated
    public static Result createContest() {
        Form<CreateContestParams> contestEntryForm = form(CreateContestParams.class).bindFromRequest();

        User theUser = (User) ctx().args.get("User");

        if (!contestEntryForm.hasErrors()) {
            CreateContestParams params = contestEntryForm.get();

            List<String> errores = new ArrayList<>();

            TemplateContest templateContest = TemplateContest.findOne(params.templateContestId);
            if (!templateContest.state.isActive()) {
                errores.add(ERROR_TEMPLATE_CONTEST_NOT_ACTIVE);
            }

            if (errores.isEmpty()) {
                boolean updatingContest = false;

                // En primer lugar, intentamos reutilizar cualquier contest de este jugador que esté sin completar...
                Contest contest = Contest.findOneWaitingAuthor(theUser.userId);
                if (contest != null) {
                    Logger.debug("createContest: Reutilizando #{}: authorId: {}", contest.contestId, theUser.userId);
                    contest.setupFromTemplateContest(templateContest);
                    updatingContest = true;
                }
                else {
                    // Creamos el contest
                    contest = new Contest(templateContest);
                    contest.contestId = new ObjectId();
                }

                if (!params.name.isEmpty()) {
                    contest.name = params.name;
                }

                // Logger.debug("createContest: {} - {}", params.templateContestId, params.name);

                // Por defecto, los contests creados por los usuarios esperarán a que el author entre un contestEntry
                contest.state = ContestState.WAITING_AUTHOR;
                contest.authorId = theUser.userId;
                contest.startDate = new DateTime(params.millisecondsSinceEpoch).withZone(DateTimeZone.UTC).toDate();
                contest.simulation = params.simulation;

                // TODO: Min. Entries a 2, hasta que se remaquete la página de Creación de Torneos
                contest.minEntries = 2;

                contest.maxEntries = params.maxEntries;
                contest.freeSlots = params.maxEntries;
                contest.entryFee = params.simulation ? Money.zero(MoneyUtils.CURRENCY_ENERGY).plus(templateContest.entryFee.getAmount()) : templateContest.entryFee;

                Model.contests().update("{_id: #}", contest.contestId).upsert().with(contest);

                // Logger.debug("createContest: contestEntry: {}", params.soccerTeam);

                // TODO: En principio crearemos un contest...
                // FIXME: Se podría devolver el contest creado sin incluirlo en la base de datos y
                // FIXME:   en una query posterior en el que se proporcione la información completa (contest + contestEntry) se haría la creación y la inserción del contestEntry
                /*
                // Añadimos el contestEntry del usuario
                List<ObjectId> idsList = ListUtils.objectIdListFromJson(params.soccerTeam);
                EnterContestJob enterContestJob = EnterContestJob.create(theUser.userId, contest.contestId, idsList);
                if (!enterContestJob.isDone()) {
                }
                */

                Contest contestCreated = Contest.findOne(contest.contestId);
                return attachInfoToContest(contestCreated).toResult(JsonViews.FullContest.class);
            }
        }

        Logger.debug("BAD");
        Object result = contestEntryForm.errorsAsJson();
        return new ReturnHelper(!contestEntryForm.hasErrors(), result).toResult();
    }

    /*
     * Devuelve la lista de contests activos (aquellos a los que un usuario puede apuntarse)
     */
    public static Result getActiveContests() throws Exception {
        List<Contest> contests = Cache.getOrElse("ActiveContests", new Callable<List<Contest>>() {
            @Override
            public List<Contest> call() throws Exception {
                return Contest.findAllActiveNotFull(JsonViews.ActiveContests.class);
                //return Contest.findAllActive(JsonViews.ActiveContests.class);
            }
        }, CACHE_ACTIVE_CONTESTS);

        // Filtrar los contests que ya están completos
        List<Contest> contestsNotFull = new ArrayList<>(contests.size());
        for (Contest contest: contests) {
            if (!contest.isFull() && !contest.isCreatedByUser()) {
                contestsNotFull.add(contest);
            }
        }

        User theUser = SessionUtils.getUserFromRequest(Controller.ctx().request());
        if (theUser != null) {
            // Quitamos los torneos en los que esté inscrito el usuario
            List<ObjectId> contestIds = contestsNotFull.stream().map(contest -> contest.contestId).collect(Collectors.toList());
            List<Contest> contestsRegistered = Contest.findSignupUser(contestIds, theUser.userId);

            contestsNotFull.removeIf( contest -> contestsRegistered.stream().anyMatch( registered -> registered.contestId.equals(contest.contestId) ));
        }

        return new ReturnHelper(ImmutableMap.of("contests", contestsNotFull)).toResult();
    }

    @UserAuthenticated
    public static Result getMyContests() {
        User theUser = (User)ctx().args.get("User");

        List<Contest> myActiveContests = Contest.findAllMyActive(theUser.userId, JsonViews.MyActiveContests.class);
        List<Contest> myLiveContests = Contest.findAllMyLive(theUser.userId, JsonViews.MyLiveContests.class);
        List<Contest> myHistoryContests = Contest.findAllMyHistory(theUser.userId, JsonViews.MyHistoryContests.class);

        List<TemplateMatchEvent> liveMatchEvents = TemplateMatchEvent.gatherFromContests(myLiveContests);

        return new ReturnHelperWithAttach()
                .attachObject("contests_0", myActiveContests, JsonViews.Public.class)
                .attachObject("contests_1", myLiveContests, JsonViews.FullContest.class)
                .attachObject("contests_2", myHistoryContests, JsonViews.Extended.class)
                .attachObject("match_events", liveMatchEvents, JsonViews.FullContest.class)
                .attachObject("profile", theUser.getProfile(), JsonViews.Public.class)
                .toResult();
    }

    @UserAuthenticated
    public static Result getMyActiveContests() {
        User theUser = (User)ctx().args.get("User");
        return new ReturnHelper(ImmutableMap.of(
                "contests", Contest.findAllMyActive(theUser.userId, JsonViews.MyActiveContests.class),
                "profile", theUser.getProfile()
        )).toResult();
    }

    @UserAuthenticated
    public static Result getMyLiveContests() throws Exception {
        User theUser = (User)ctx().args.get("User");

        // Comprobar si existe un torneo en live, antes de hacer una query más costosa (y específico a un usuario)
        boolean existsLive = Cache.getOrElse("ExistsContestInLive", new Callable<Boolean>() {
            @Override
            public Boolean call() throws Exception {
                return TemplateContest.existsAnyInState(ContestState.LIVE);
            }
        }, CACHE_EXISTS_LIVE);

        Result result;
        if (existsLive) {
            List<Contest> myLiveContests = Contest.findAllMyLive(theUser.userId, JsonViews.MyLiveContests.class);

            List<TemplateMatchEvent> liveMatchEvents = TemplateMatchEvent.gatherFromContests(myLiveContests);

            result = new ReturnHelperWithAttach()
                    .attachObject("contests", myLiveContests, JsonViews.FullContest.class)
                    .attachObject("match_events", liveMatchEvents, JsonViews.FullContest.class)
                    .attachObject("profile", theUser.getProfile(), JsonViews.Public.class)
                    .toResult();
        }
        else {
            // Si no existe ningún torneo Live, no puede existir ninguno en el que esté el usuario
            result = new ReturnHelper(ImmutableMap.of(
                    "contests", new ArrayList<Contest>(),
                    "match_events", new ArrayList<Contest>(),
                    "profile", theUser.getProfile()
            )).toResult();
        }
        return result;
    }

    @UserAuthenticated
    public static Result getMyHistoryContests() {
        User theUser = (User)ctx().args.get("User");
        return new ReturnHelper(ImmutableMap.of(
                "contests", Contest.findAllMyHistory(theUser.userId, JsonViews.MyHistoryContests.class),
                "profile", theUser.getProfile()
        )).toResult(JsonViews.Extended.class);
    }

    @UserAuthenticated
    public static Result getMyHistoryContestEntry(String contestId) {
        User theUser = (User)ctx().args.get("User");
        return new ReturnHelper(ImmutableMap.of(
                "contest", Contest.findOneMyHistoryWithMyEntry(new ObjectId(contestId), theUser.userId, JsonViews.MyHistoryContests.class)
        )).toResult(JsonViews.MyHistoryContests.class);
    }

    @UserAuthenticated
    public static Result countMyLiveContests() throws Exception {
        User theUser = (User)ctx().args.get("User");

        boolean existsLive = Cache.getOrElse("ExistsContestInLive", new Callable<Boolean>() {
            @Override
            public Boolean call() throws Exception {
                return TemplateContest.existsAnyInState(ContestState.LIVE);
            }
        }, CACHE_EXISTS_LIVE);

        long count = !existsLive ? 0 : Contest.countByState(theUser.userId, ContestState.LIVE);
        return new ReturnHelper(ImmutableMap.of("count", count))
                .toResult();
    }

    @UserAuthenticated
    public static Result countMyContests() throws Exception {
        User theUser = (User)ctx().args.get("User");

        // Comprobar si existe un torneo en live, antes de hacer una query más costosa (y específico a un usuario)
        boolean existsLive = Cache.getOrElse("ExistsContestInLive", new Callable<Boolean>() {
            @Override
            public Boolean call() throws Exception {
                return TemplateContest.existsAnyInState(ContestState.LIVE);
            }
        }, CACHE_EXISTS_LIVE);

        return new ReturnHelper(ImmutableMap.of(
                "numWaiting", 0, //Contest.countByState(theUser.userId, ContestState.ACTIVE),
                "numLive", !existsLive ? 0 : Contest.countByState(theUser.userId, ContestState.LIVE),
                "numVirtualHistory", 0, //Contest.countByStateAndSimulation(theUser.userId, ContestState.HISTORY, true),
                "numRealHistory", 0 //Contest.countByStateAndSimulation(theUser.userId, ContestState.HISTORY, false)
        ))
        .toResult();
    }

    @UserAuthenticated
    public static Result getMyActiveContest(String contestId) {
        User theUser = (User)ctx().args.get("User");
        Contest contest = Contest.findOne(contestId);
        if (!contest.containsContestEntryWithUser(theUser.userId)) {
            Logger.error("WTF 7943: getMyContest: contest: {} user: {}", contestId, theUser.userId);
            return new ReturnHelper(false, ERROR_MY_CONTEST_INVALID).toResult();
        }

        // Quitamos todos fantasyTeam de los contestEntries que no sean del User
        for (ContestEntry contestEntry: contest.contestEntries) {
            if (!contestEntry.userId.equals(theUser.userId)) {
                contestEntry.soccerIds.clear();
            }
        }

        return attachInfoToContest(contest).toResult(JsonViews.FullContest.class);
    }

    public static Result getMyLiveContest(String contestId) throws Exception {
        return Cache.getOrElse("ViewLiveContest-".concat(contestId), new Callable<Result>() {
            @Override
            public Result call() throws Exception {
                return getViewContest(contestId);
            }
        }, CACHE_VIEW_LIVE_CONTESTS);
    }

    public static Result getMyHistoryContest(String contestId) throws Exception {
        return Cache.getOrElse("ViewHistoryContest-".concat(contestId), new Callable<Result>() {
            @Override
            public Result call() throws Exception {
                return getViewContest(contestId);
            }
        }, CACHE_VIEW_HISTORY_CONTEST);
    }

    public static Result getViewContest(String contestId) {
        User theUser = (User)ctx().args.get("User");
        Contest contest = Contest.findOne(contestId);

        // No se puede ver el contest "completo" si está "activo" (únicamente en "live" o "history")
        if (contest.state.isActive()) {
            Logger.error("WTF 7945: getViewContest: contest: {} user: {}", contestId, theUser != null ? theUser.userId : "<guest>");
            return new ReturnHelper(false, ERROR_VIEW_CONTEST_INVALID).toResult();
        }

        List<UserInfo> usersInfoInContest = UserInfo.findAllFromContestEntries(contest.contestEntries);
        List<TemplateMatchEvent> matchEvents = TemplateMatchEvent.findAll(contest.templateMatchEventIds);

        ImmutableMap.Builder<Object, Object> builder = ImmutableMap.builder()
                .put("contest", contest)
                .put("users_info", usersInfoInContest)
                .put("match_events", matchEvents)
                .put("prizes", Prizes.findOne(contest.prizeType, contest.getNumEntries(), contest.getPrizePool()));

        return new ReturnHelper(builder.build())
                .toResult(JsonViews.FullContest.class);
    }

    @UserAuthenticated
    public static Result getMyContestEntry(String contestId) {
        User theUser = (User)ctx().args.get("User");
        Contest contest = Contest.findOne(contestId);
        if (!contest.containsContestEntryWithUser(theUser.userId)) {
            Logger.error("WTF 7944: getMyContestEntry: contest: {} user: {}", contestId, theUser.userId);
            return new ReturnHelper(false, ERROR_MY_CONTEST_ENTRY_INVALID).toResult();
        }

        Set<ObjectId> playersInContestEntry = new HashSet<>();

        // Registramos los players seleccionados por el User y Quitamos todos fantasyTeam de los contestEntries que no sean del User
        for (ContestEntry contestEntry: contest.contestEntries) {
            if (contestEntry.userId.equals(theUser.userId)) {
                playersInContestEntry.addAll(contestEntry.soccerIds);
            }
            else {
                contestEntry.soccerIds.clear();
            }
        }

        List<UserInfo> usersInfoInContest = UserInfo.findAllFromContestEntries(contest.contestEntries);
        List<TemplateMatchEvent> matchEvents = TemplateMatchEvent.findAll(contest.templateMatchEventIds);

        return new ReturnHelper(ImmutableMap.of("contest", contest,
                "users_info", usersInfoInContest,
                "match_events", matchEvents))
                .toResult(JsonViews.FullContest.class);
    }

    public static Result getContestInfo(String contestId) throws Exception {
        return Cache.getOrElse("ContestInfo-".concat(contestId), new Callable<Result>() {
            @Override
            public Result call() throws Exception {
                Contest contest = Contest.findOne(contestId);
                List<UserInfo> usersInfoInContest = UserInfo.findAllFromContestEntries(contest.contestEntries);
                List<TemplateMatchEvent> matchEvents = TemplateMatchEvent.findAll(contest.templateMatchEventIds);

                return new ReturnHelper(ImmutableMap.of(
                        "contest", contest,
                        "users_info", usersInfoInContest,
                        "match_events", matchEvents,
                        "prizes", Prizes.findOne(contest)))
                        .toResult(JsonViews.ContestInfo.class);
            }
        }, CACHE_CONTEST_INFO);
    }

    public static Result getActiveContest(String contestId) throws Exception {
        return Cache.getOrElse("ActiveContest-".concat(contestId), new Callable<Result>() {
            @Override
            public Result call() throws Exception {
                return attachInfoToContest(Contest.findOne(contestId)).toResult(JsonViews.Extended.class);
            }
        }, CACHE_ACTIVE_CONTEST);
    }

    private static ReturnHelper attachInfoToContest(Contest contest) {
        List<UserInfo> usersInfoInContest = UserInfo.findAllFromContestEntries(contest.contestEntries);
        List<TemplateMatchEvent> matchEvents = TemplateMatchEvent.findAll(contest.templateMatchEventIds);

        return new ReturnHelper(ImmutableMap.of("contest", contest,
                                                "users_info", usersInfoInContest,
                                                "match_events", matchEvents));
    }

    /**
     * Obtener los partidos "live" correspondientes a un template contest
     *  Incluye los fantasy points obtenidos por cada uno de los futbolistas
     *  Queremos recibir un TemplateContestID, y no un ContestID, dado que el Template es algo generico
     *      y valido para todos los usuarios que esten apuntados a varios contests (creados a partir del mismo Template)
     *  Los documentos "LiveMatchEvent" referencian los partidos del template (facilita la query)
     * @param templateContestId TemplateContest sobre el que se esta interesado
     * @return La lista de partidos "live"
     */
    public static Result getLiveMatchEventsFromTemplateContest(String templateContestId) throws Exception {
        return Cache.getOrElse("LiveMatchEventsFromTemplateContest-".concat(templateContestId), new Callable<Result>() {
            @Override
            public Result call() throws Exception {
                // Obtenemos el TemplateContest
                TemplateContest templateContest = TemplateContest.findOne(templateContestId);

                if (templateContest == null) {
                    return new ReturnHelper(false, ERROR_TEMPLATE_CONTEST_INVALID).toResult();
                }

                // Consultar por los partidos del TemplateContest (queremos su version "live")
                List<TemplateMatchEvent> liveMatchEventList = TemplateMatchEvent.findAllPlaying(templateContest.templateMatchEventIds);

                return new ReturnHelper(liveMatchEventList).toResult(JsonViews.FullContest.class);
            }
        }, CACHE_LIVE_MATCHEVENTS);
    }

    public static Result getLiveMatchEventsFromContest(String contestId) {

        // Obtenemos el Contest
        Contest contest = Contest.findOne(contestId);

        if (contest == null) {
            return new ReturnHelper(false, ERROR_TEMPLATE_CONTEST_INVALID).toResult();
        }

        // Consultar por los partidos del TemplateContest (queremos su version "live")
        List<TemplateMatchEvent> liveMatchEventList = TemplateMatchEvent.findAllPlaying(contest.templateMatchEventIds);

        return new ReturnHelper(liveMatchEventList).toResult(JsonViews.FullContest.class);
    }

    public static Result getLiveContestEntries(String contestId) throws Exception {

        return Cache.getOrElse("LiveContestEntries-".concat(contestId), new Callable<Result>() {
            @Override
            public Result call() throws Exception {
                // Obtenemos el Contest
                Contest contest = Contest.findOne(contestId);

                if (contest == null) {
                    return new ReturnHelper(false, ERROR_CONTEST_INVALID).toResult();
                }

                if (!contest.state.isLive() && !contest.state.isHistory()) {
                    return new ReturnHelper(false, ERROR_CONTEST_INVALID).toResult();
                }

                return new ReturnHelper(ImmutableMap.of(
                        "contest_entries", contest.contestEntries))
                        .toResult(JsonViews.FullContest.class);
            }
        }, CACHE_LIVE_CONTESTENTRIES);
    }

    // @UserAuthenticated
    public static Result getSoccerPlayersAvailablesToChange(String contestId) {
        User theUser = (User)ctx().args.get("User");
        Contest contest = Contest.findOne(contestId);

        /*
        if (!contest.containsContestEntryWithUser(theUser.userId)) {
            return new ReturnHelper(false, ERROR_OP_UNAUTHORIZED).toResult();
        }
        */

        // Obtener los partidos del torneo
        List<TemplateMatchEvent> matchEvents = TemplateMatchEvent.findAll(contest.templateMatchEventIds);

        // Quedarnos con los partidos que no hayan terminado
        matchEvents = matchEvents.stream().filter( matchEvent -> !matchEvent.isGameFinished()).collect(Collectors.toList());

        List<TemplateSoccerTeam> teams = TemplateSoccerTeam.findAllFromMatchEvents(matchEvents);
        Set<ObjectId> teamIds = new HashSet<>();
        for(TemplateSoccerTeam team : teams) {
            teamIds.add(team.templateSoccerTeamId);
        }

        // Buscar todos los players de la posición indicada y de los partidos no terminados
        List<InstanceSoccerPlayer> instanceSoccerPlayers = new ArrayList<>();

        contest.instanceSoccerPlayers.forEach( instance -> {
            if (teamIds.contains(instance.templateSoccerTeamId)) {
                instanceSoccerPlayers.add(instance);
            }
        });

        return new ReturnHelper(ImmutableMap.builder()
                .put("instanceSoccerPlayers", instanceSoccerPlayers)
                .put("match_events", matchEvents)
                .build())
                .toResult(JsonViews.FullContest.class);
    }
}
