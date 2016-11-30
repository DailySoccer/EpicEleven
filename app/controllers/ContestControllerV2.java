package controllers;

import actions.AllowCors;
import actions.UserAuthenticated;
import com.google.common.collect.ImmutableMap;
import model.*;
import org.bson.types.ObjectId;
import play.Logger;
import play.cache.Cache;
import play.cache.Cached;
import play.mvc.Controller;
import play.mvc.Result;
import play.mvc.With;
import utils.*;

import java.util.*;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;

@AllowCors.Origin
public class ContestControllerV2 extends Controller {
    private final static int CACHE_ACTIVE_TEMPLATE_CONTESTS = 60 * 60 * 8;      // 8 horas
    private final static int CACHE_COUNT_ACTIVE_TEMPLATE_CONTESTS = 60 * 30;    // 30 minutos
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
     * Devuelve la lista de template Contests disponibles
     */
    public static Result getActiveTemplateContests() throws Exception {

        Map<String, Object> result = Cache.getOrElse("ActiveTemplateContests", new Callable<Map<String, Object>>() {
            @Override
            public Map<String, Object> call() throws Exception {
                return findActiveTemplateContests();
            }
        }, CACHE_ACTIVE_TEMPLATE_CONTESTS);

        // Necesitamos actualizar la caché?
        long countTemplateContest = TemplateContest.countAllActiveOrLive();
        if (result.containsKey("template_contests") && result.get("template_contests") instanceof ArrayList){
            List<?> list = (List<?>) result.get("template_contests");
            if (list != null && list.size() != countTemplateContest) {
                result = findActiveTemplateContests();

                Cache.set("ActiveTemplateContests", result);
                Logger.debug("getActiveTemplateContests: cache INVALID");
            }
        }

        return new ReturnHelper(result).toResult(JsonViews.Extended.class);
    }

    private static Map<String, Object> findActiveTemplateContests() {
        List<TemplateContest> templateContests = TemplateContest.findAllActiveOrLive();
        List<TemplateMatchEvent> matchEvents = TemplateMatchEvent.gatherFromTemplateContests(templateContests);
        return ImmutableMap.of(
                "template_contests", templateContests,
                "match_events", matchEvents
        );
    }


    @With(AllowCors.CorsAction.class)
    @Cached(key = "CountActiveTemplateContests", duration = CACHE_COUNT_ACTIVE_TEMPLATE_CONTESTS)
    public static Result countActiveTemplateContests() {
        return new ReturnHelper(ImmutableMap.of(
                "count", TemplateContest.countAllActiveOrLive()
        )).toResult();
    }

    public static Result getActiveContestsV2() throws Exception {
        List<Contest> contests = Cache.getOrElse("ActiveContestsV2", new Callable<List<Contest>>() {
            @Override
            public List<Contest> call() throws Exception {
                return Contest.findAllActiveNotFull(JsonViews.ActiveContestsV2.class);
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

        return new ReturnHelper(ImmutableMap.of("contests", contestsNotFull)).toResult(JsonViews.ActiveContestsV2.class);
    }

    @UserAuthenticated
    public static Result getMyActiveContestsV2() {
        User theUser = (User)ctx().args.get("User");
        return new ReturnHelper(ImmutableMap.of(
                "contests", Contest.findAllMyActive(theUser.userId, JsonViews.MyActiveContestsV2.class)
        )).toResult(JsonViews.MyActiveContestsV2.class);
    }

    @UserAuthenticated
    public static Result getMyLiveContestsV2() throws Exception {
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
            List<Contest> myLiveContests = Contest.findAllMyLive(theUser.userId, JsonViews.MyLiveContestsV2.class);

            List<TemplateMatchEvent> liveMatchEvents = TemplateMatchEvent.gatherFromContests(myLiveContests);

            return new ReturnHelperWithAttach()
                    .attachObject("contests", myLiveContests, JsonViews.MyLiveContestsV2.class)
                    .attachObject("match_events", liveMatchEvents, JsonViews.FullContest.class)
                    .toResult();
        }
        else {
            // Si no existe ningún torneo Live, no puede existir ninguno en el que esté el usuario
            result = new ReturnHelper(ImmutableMap.of(
                    "contests", new ArrayList<Contest>(),
                    "match_events", new ArrayList<Contest>()
            )).toResult();
        }
        return result;
    }

    @UserAuthenticated
    public static Result getMyHistoryContestsV2() {
        User theUser = (User)ctx().args.get("User");
        return new ReturnHelper(ImmutableMap.of(
                "contests", Contest.findAllMyHistoryWithMyEntry(theUser.userId, JsonViews.MyHistoryContests.class)
        )).toResult(JsonViews.MyHistoryContests.class);
    }

    @UserAuthenticated
    public static Result getMyActiveContestV2(String contestId) {
        User theUser = (User)ctx().args.get("User");
        Contest contest = Contest.findOne(new ObjectId(contestId), theUser.userId, JsonViews.MyActiveContest.class);
        if (!contest.containsContestEntryWithUser(theUser.userId)) {
            Logger.error("WTF 7943: getMyContest: contest: {} user: {}", contestId, theUser.userId);
            return new ReturnHelper(false, ERROR_MY_CONTEST_INVALID).toResult();
        }

        return new ReturnHelper(ImmutableMap.of("contest", contest)).toResult(JsonViews.MyActiveContest.class);
    }

    @UserAuthenticated
    public static Result getMyContestEntryV2(String contestId) {
        User theUser = (User)ctx().args.get("User");
        Contest contest = Contest.findOne(new ObjectId(contestId), theUser.userId, JsonViews.MyActiveContest.class);
        if (!contest.containsContestEntryWithUser(theUser.userId)) {
            Logger.error("WTF 7944: getMyContestEntry: contest: {} user: {}", contestId, theUser.userId);
            return new ReturnHelper(false, ERROR_MY_CONTEST_ENTRY_INVALID).toResult();
        }

        return new ReturnHelper(ImmutableMap.of("contest", contest)).toResult(JsonViews.MyActiveContest.class);
    }

    public static Result getContestInfoV2(String contestId) throws Exception {
        return Cache.getOrElse("ContestInfo-".concat(contestId), new Callable<Result>() {
            @Override
            public Result call() throws Exception {
                Contest contest = Contest.findOne(contestId);
                List<UserInfo> usersInfoInContest = UserInfo.findAllFromContestEntries(contest.contestEntries);

                return new ReturnHelper(ImmutableMap.of(
                        "contest", contest,
                        "users_info", usersInfoInContest,
                        "prizes", Prizes.findOne(contest)))
                        .toResult(JsonViews.ContestInfo.class);
            }
        }, CACHE_CONTEST_INFO);
    }

    public static Result getActiveContestV2(String contestId) throws Exception {
        return Cache.getOrElse("ActiveContestV2-".concat(contestId), new Callable<Result>() {
            @Override
            public Result call() throws Exception {
                Contest contest = Contest.findOne(new ObjectId(contestId), JsonViews.ActiveContest.class);
                return new ReturnHelper(ImmutableMap.of("contest", contest)).toResult(JsonViews.ActiveContest.class);
            }
        }, CACHE_ACTIVE_CONTEST);
    }
}
