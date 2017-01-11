package controllers;

import actions.AllowCors;
import actions.UserAuthenticated;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import model.*;
import org.bson.types.ObjectId;
import play.Logger;
import play.cache.Cache;
import play.mvc.Controller;
import play.mvc.Result;
import utils.*;

import java.util.*;
import java.util.stream.Collectors;

import play.libs.F.Promise;

import static akka.pattern.Patterns.ask;

@AllowCors.Origin
public class ContestControllerV2 extends Controller {
    private final static int ACTOR_TIMEOUT = 10000;
    private final static int CACHE_ACTIVE_TEMPLATE_CONTESTS = 60 * 60 * 8;      // 8 horas
    private final static int CACHE_COUNT_ACTIVE_TEMPLATE_CONTESTS = 60 * 30;    // 30 minutos
    private final static int CACHE_ACTIVE_CONTESTS = 60;
    private final static int CACHE_ACTIVE_CONTEST = 60;
    private final static int CACHE_VIEW_LIVE_CONTESTS = 60 * 30 * 1;        // 30 minutos
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
    public static Promise<Result> getActiveTemplateContests() throws Exception {
        return QueryManager.getActiveTemplateContests()
                .map(response -> new ReturnHelper(response).toResult(JsonViews.Extended.class));
    }

    public static Promise<Result> countActiveTemplateContests() {
        return QueryManager.countActiveTemplateContests()
                .map(response -> (Result) response);
    }

    public static Promise<Result> getActiveContestsV2() throws Exception {
        return QueryManager.getActiveContestsV2()
                .map( response -> {
                    List<Contest> contests = (List<Contest>) response;

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
                });
    }

    @UserAuthenticated
    public static Result getMyActiveContestsV2() {
        User theUser = (User)ctx().args.get("User");
        return new ReturnHelper(ImmutableMap.of(
                "contests", Contest.findAllMyActive(theUser.userId, JsonViews.MyActiveContestsV2.class)
        )).toResult(JsonViews.MyActiveContestsV2.class);
    }

    @UserAuthenticated
    public static Promise<Result> getMyLiveContestsV2() throws Exception {
        User theUser = (User)ctx().args.get("User");

        return QueryManager.existsContestInLive()
                .flatMap(response -> {
                    boolean existsLive = (Boolean) response;

                    if (existsLive) {
                        return QueryManager.getMyLiveContestsV2(theUser.userId.toString())
                                .map(response2 -> (Result) response2);
                    }

                    // Si no existe ningún torneo Live, no puede existir ninguno en el que esté el usuario
                    return Promise.promise( () -> new ReturnHelper(ImmutableMap.of(
                            "contests", new ArrayList<Contest>()
                    )).toResult() );
                });
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

    public static Promise<Result> getContestInfoV2(String contestId) throws Exception {
        return QueryManager.getContestInfoV2(contestId)
                .map(response -> (Result) response);
    }

    public static Promise<Result> getActiveContestV2(String contestId) throws Exception {
        return QueryManager.getActiveContestV2(contestId)
                .map(response -> (Result) response);
    }

    public static Promise<Result> getMyLiveContestV2(String contestId) throws Exception {
        ObjectId userId = SessionUtils.getUserIdFromRequest(Controller.ctx().request());

        return QueryManager.getMyLiveContestV2(userId != null ? userId.toString() : null, contestId)
                .map(response -> (Result) response);
    }

}
