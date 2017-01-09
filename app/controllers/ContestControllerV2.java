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
                .map(response -> {
                    boolean existsLive = (Boolean) response;

                    if (existsLive) {
                        final List<Contest> myLiveContests = new ArrayList<>();

                        // Solicitamos los contests en los que estamos apuntados (incluye información de tiempo de actualización del "live")
                        List<Contest> myLiveContestIds = Contest.findMyLiveUpdated(theUser.userId);

                        // Contests que tendremos que obtener
                        List<ObjectId> myLiveIdsToUpdate = new ArrayList<>();

                        myLiveContestIds.forEach(contest -> {
                            Contest contestCached = (Contest) Cache.get(contest.contestId.toString().concat("live"));
                            if (contestCached != null) {
                                // El tiempo de caché está correctamente actualizada ?
                                if (contestCached.liveUpdatedAt.compareTo(contest.liveUpdatedAt) >= 0) {
                                    Logger.debug("myLiveContests: {} OK : {} >= {}",
                                            contest.contestId, GlobalDate.formatDate(contestCached.liveUpdatedAt), GlobalDate.formatDate(contest.liveUpdatedAt));
                                    myLiveContests.add(contestCached);
                                } else {
                                    Logger.debug("myLiveContests: {} FAILED : {} < {}",
                                            contest.contestId, GlobalDate.formatDate(contestCached.liveUpdatedAt), GlobalDate.formatDate(contest.liveUpdatedAt));
                                    myLiveIdsToUpdate.add(contest.contestId);
                                }
                            } else {
                                myLiveIdsToUpdate.add(contest.contestId);
                            }
                        });

                        List<Contest> myLiveContestsToUpdate = getLiveInfo(myLiveIdsToUpdate);
                        myLiveContests.addAll(myLiveContestsToUpdate);

                        List<Contest> result = myLiveContests.stream().map(contest -> {
                            // Clonamos los torneos a devolver, para modificarlos y no afectar a lo registrado en la caché
                            Contest contestFiltered = new Contest(contest);

                            contestFiltered.contestId = contest.contestId;

                            // Eliminar los contestEntries que no sean el usuario
                            contestFiltered.contestEntries = contest.contestEntries.stream().filter(contestEntry -> contestEntry.userId.equals(theUser.userId)).collect(Collectors.toList());

                            // Una vez calculado el ranking ya no estamos intesados en enviarlos al app
                            contestFiltered.templateMatchEventIds = null;

                            return contestFiltered;
                        }).collect(Collectors.toList());

                        return new ReturnHelperWithAttach()
                                .attachObject("contests", result, JsonViews.MyLiveContestsV2.class)
                                .toResult();
                    }

                    // Si no existe ningún torneo Live, no puede existir ninguno en el que esté el usuario
                    return new ReturnHelper(ImmutableMap.of(
                            "contests", new ArrayList<Contest>()
                    )).toResult();
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

    @UserAuthenticated
    public static Result getMyLiveContestV2(String contestId) throws Exception {
        User theUser = (User)ctx().args.get("User");

        Contest contestCached = null;

        Map<Object, Object> resultCached = (Map<Object, Object>) Cache.get("ViewLiveContestV2-".concat(contestId));
        if (resultCached != null) {
            contestCached = (Contest) resultCached.get("contest");

            Contest infoLive = Contest.findOneLiveUpdated(new ObjectId(contestId));
            if (contestCached.liveUpdatedAt.compareTo(infoLive.liveUpdatedAt) < 0) {

                // No nos sirve la caché que tenemos registrada
                contestCached = null;
            }
            else {
                Logger.debug("ViewLiveContest: {} OK : {} >= {}",
                        contestCached.contestId, GlobalDate.formatDate(contestCached.liveUpdatedAt), GlobalDate.formatDate(infoLive.liveUpdatedAt));
            }
        }

        if (contestCached == null) {

            contestCached = (Contest) Cache.get(contestId.concat("live"));
            if (contestCached == null) {
                List<Contest> myLiveContestsToUpdate = getLiveInfo(ImmutableList.of(new ObjectId(contestId)));
                contestCached = myLiveContestsToUpdate.get(0);
            }
            else {
                Logger.debug("myLiveContest: {} OK", contestCached.contestId);
            }

            // No se puede ver el contest "completo" si está "activo" (únicamente en "live" o "history")
            if (contestCached.state.isActive()) {
                Logger.error("WTF 7945: getViewContest: contest: {} user: {}", contestId, theUser != null ? theUser.userId : "<guest>");
                return new ReturnHelper(false, ERROR_VIEW_CONTEST_INVALID).toResult();
            }

            List<UserInfo> usersInfoInContest = UserInfo.findNicknamesFromContestEntries(contestCached.contestEntries);

            // Creamos un mapa con los nickNames
            Map<ObjectId, String> usersInfoMap = new HashMap<>();
            usersInfoInContest.stream().forEach( userInfo -> usersInfoMap.put(userInfo.userId, userInfo.nickName) );

            // Incrustamos el nickName en el contestEntry, para evitar enviarlos como datos independientes
            contestCached.contestEntries.forEach( contestEntry -> {
                if (usersInfoMap.containsKey(contestEntry.userId)) {
                    contestEntry.nickName = usersInfoMap.get(contestEntry.userId);
                }
            });

            List<TemplateMatchEvent> matchEvents = TemplateMatchEvent.findAll(contestCached.templateMatchEventIds);

            ImmutableMap.Builder<Object, Object> builder = ImmutableMap.builder()
                    .put("contest", contestCached)
                    .put("match_events", matchEvents)
                    /*.put("prizes", Prizes.findOne(contestCached.prizeType, contestCached.getNumEntries(), contestCached.getPrizePool()))*/;

            resultCached = builder.build();

            Cache.set("ViewLiveContestV2-".concat(contestId), resultCached, CACHE_VIEW_LIVE_CONTESTS);

            Logger.debug("ViewLiveContest: {} UPDATED", contestCached.contestId);
        }

        return new ReturnHelper(resultCached)
                .toResult(JsonViews.FullContest.class);
    }

    static private List<Contest> getLiveInfo(List<ObjectId> idList) {
        List<Contest> liveContests = new ArrayList<>();

        idList.forEach( objectId -> {
            liveContests.add( Contest.findOne(objectId, JsonViews.MyLiveContestsV2.class) );
        });
        if (!liveContests.isEmpty()) {
            List<TemplateMatchEvent> liveMatchEvents = TemplateMatchEvent.gatherFromContests(liveContests);

            Logger.debug("myLiveContestsToUpdate: {} liveMatchEvents: {}", liveContests.size(), liveMatchEvents.size());

            // Unificamos a los futbolistas en un único Map
            Map<String, LiveFantasyPoints> liveFantasyPointsMap = new HashMap<>();
            liveMatchEvents.forEach( live -> liveFantasyPointsMap.putAll(live.liveFantasyPoints) );

            // Registramos los fantasyPoints y su posición actual en cada contestEntry
            liveContests.forEach( contest -> {
                contest.contestEntries.forEach( contestEntry -> {
                    contestEntry.fantasyPoints = contestEntry.getFantasyPointsFromMap(liveFantasyPointsMap);

                    // Una vez calculados los fantasyPoints ya no estamos intesados en enviarlos al app
                    // contestEntry.soccerIds = null;
                });

                Collections.sort(contest.contestEntries, (ContestEntry c1, ContestEntry c2) -> {
                    int compare = Integer.compare(c2.fantasyPoints, c1.fantasyPoints);
                    // Si tienen los mismos fantasyPoints, los diferenciaremos por su contestEntryId
                    return compare != 0 ? compare : c2.contestEntryId.toString().compareTo(c1.contestEntryId.toString());
                });

                for (int i=0; i<contest.contestEntries.size(); i++) {
                    contest.contestEntries.get(i).position = i;
                }

                Cache.set(contest.contestId.toString().concat("live"), contest, CACHE_VIEW_LIVE_CONTESTS);

                Logger.debug("myLiveContests: {} UPDATED", contest.contestId);
            });
        }
        return liveContests;
    }
}
