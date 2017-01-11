package controllers;

import akka.actor.UntypedActor;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import model.*;
import org.bson.types.ObjectId;
import play.Logger;
import play.cache.Cache;
import play.mvc.Result;
import utils.ListUtils;
import utils.ReturnHelper;
import utils.ReturnHelperWithAttach;

import java.util.*;
import java.util.stream.Collectors;

public class QueryActor extends UntypedActor {

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
    private final static int CACHE_SOCCERPLAYER_BY_COMPETITION = 15 * 60;   // 15 minutos
    private final static int CACHE_TEMPLATESOCCERPLAYERS = 8 * 60 * 60;     // 8 Horas
    private final static int CACHE_TEMPLATESOCCERPLAYER = 8 * 60 * 60;          // 8 Hora
    private final static int CACHE_TEMPLATESOCCERTEAMS = 24 * 60 * 60;
    private final static int CACHE_LEADERBOARD = 12 * 60 * 60;              // 12 horas

    private static final String ERROR_VIEW_CONTEST_INVALID = "ERROR_VIEW_CONTEST_INVALID";
    private static final String ERROR_MY_CONTEST_INVALID = "ERROR_MY_CONTEST_INVALID";
    private static final String ERROR_MY_CONTEST_ENTRY_INVALID = "ERROR_MY_CONTEST_ENTRY_INVALID";
    private static final String ERROR_TEMPLATE_CONTEST_INVALID = "ERROR_TEMPLATE_CONTEST_INVALID";
    private static final String ERROR_CONTEST_INVALID = "ERROR_CONTEST_INVALID";
    private static final String ERROR_TEMPLATE_CONTEST_NOT_ACTIVE = "ERROR_TEMPLATE_CONTEST_NOT_ACTIVE";
    private static final String ERROR_OP_UNAUTHORIZED = "ERROR_OP_UNAUTHORIZED";

    public static class CacheMsg {
        public String msg;
        public String userId;
        public Object param;

        public CacheMsg(String m, String u, Object p) { msg = m; userId = u; param = p; }
        public CacheMsg(String m, Object p) { msg = m; param = p; }
    }


    public QueryActor() {}

    @Override public void preStart() throws Exception {
        super.preStart();
    }

    @Override
    public void onReceive(Object msg) {

        try {
            if (msg instanceof CacheMsg) {
                onReceive((CacheMsg) msg);
            } else {
                onReceive((String) msg);
            }
        }
        catch (Exception exc) {
            Logger.debug("WTF 1101: CacheActor: {}", msg.toString(), exc);
            sender().tell(new akka.actor.Status.Failure(exc), getSelf());
        }
    }


    private void onReceive(CacheMsg msg) throws Exception {

        // Logger.debug("CacheActor: {}: {}", msg.msg, msg.param);

        switch (msg.msg) {
            case "getSoccerPlayersByCompetition":
                String competitionId = (String) msg.param;
                sender().tell(msgGetSoccerPlayersByCompetition((String) msg.param), getSelf());
                break;

            case "getActiveContestV2":
                sender().tell(msgGetActiveContestV2((String) msg.param), getSelf());
                break;

            case "getMyLiveContestsV2":
                sender().tell(msgGetMyLiveContestsV2(msg.userId), getSelf());
                break;

            case "getMyLiveContestV2":
                sender().tell(msgGetMyLiveContestV2(msg.userId, (String) msg.param), getSelf());
                break;

            case "getContestInfoV2":
                sender().tell(msgGetContestInfoV2((String) msg.param), getSelf());
                break;

            case "getTemplateSoccerPlayerInfo":
                sender().tell(msgGetTemplateSoccerPlayerInfo((String) msg.param), getSelf());
                break;

            case "getUserRankingList":
                sender().tell(msgGetUserRankingList((String) msg.userId), getSelf());
                break;

            default:
                unhandled(msg);
                break;
        }
    }

    private void onReceive(String msg) throws Exception {

        // Logger.debug("CacheActor: {}", msg);

        switch (msg) {
            case "getActiveTemplateContests":
                sender().tell(msgGetActiveTemplateContests(), getSelf());
                break;

            case "checkActiveTemplateContests":
                msgCheckActiveTemplateContests();
                break;

            case "countActiveTemplateContests":
                sender().tell(msgCountActiveTemplateContests(), getSelf());
                break;

            case "getActiveContestsV2":
                sender().tell(msgGetActiveContestsV2(), getSelf());
                break;

            case "getTemplateSoccerPlayersV2":
                sender().tell(msgGetTemplateSoccerPlayersV2(), getSelf());
                break;

            case "getTemplateSoccerTeams":
                sender().tell(msgGetTemplateSoccerTeams(), getSelf());
                break;

            case "existsContestInLive":
                sender().tell(msgExistsContestInLive(), getSelf());
                break;

            default:
                unhandled(msg);
                break;
        }
    }

    private static Map<String, Object> activeTemplateContestsCache() throws Exception {
        return Cache.getOrElse("ActiveTemplateContests", QueryActor::findActiveTemplateContests, CACHE_ACTIVE_TEMPLATE_CONTESTS);
    }

    private static Map<String, Object> msgGetActiveTemplateContests() throws Exception {
        return activeTemplateContestsCache();
    }

    private static void msgCheckActiveTemplateContests() throws Exception {
        Map<String, Object> result = activeTemplateContestsCache();

        // Necesitamos actualizar la caché?
        long countTemplateContest = TemplateContest.countAllActiveOrLive();
        if (result.containsKey("template_contests") && result.get("template_contests") instanceof ArrayList) {
            List<?> list = (List<?>) result.get("template_contests");
            if (list != null && list.size() != countTemplateContest) {

                Cache.set("ActiveTemplateContests", findActiveTemplateContests());
                Logger.debug("getActiveTemplateContests: cache INVALID");
            }
        }
    }

    private static Result msgCountActiveTemplateContests() throws Exception {
        return Cache.getOrElse("CountActiveTemplateContests", () -> new ReturnHelper(ImmutableMap.of(
                "count", TemplateContest.countAllActiveOrLive()
        )).toResult(), CACHE_COUNT_ACTIVE_TEMPLATE_CONTESTS);
    }

    private static List<Contest> msgGetActiveContestsV2() throws Exception {
        return Cache.getOrElse("ActiveContestsV2", () -> {
            return Contest.findAllActiveNotFull(JsonViews.ActiveContestsV2.class);
            //return Contest.findAllActive(JsonViews.ActiveContests.class);
        }, CACHE_ACTIVE_CONTESTS);
    }

    private static Result msgGetActiveContestV2(String contestId) throws Exception {
        return Cache.getOrElse("ActiveContestV2-".concat(contestId), () -> {
            Contest contest = Contest.findOne(new ObjectId(contestId), JsonViews.ActiveContest.class);
            return new ReturnHelper(ImmutableMap.of("contest", contest)).toResult(JsonViews.ActiveContest.class);
        }, CACHE_ACTIVE_CONTEST);
    }

    private static Result msgGetMyLiveContestsV2(String userIdStr) throws Exception {
        final ObjectId userId = new ObjectId(userIdStr);
        final List<Contest> myLiveContests = new ArrayList<>();

        // Solicitamos los contests en los que estamos apuntados (incluye información de tiempo de actualización del "live")
        List<Contest> myLiveContestIds = Contest.findMyLiveUpdated(userId);

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
            contestFiltered.contestEntries = contest.contestEntries.stream().filter(contestEntry -> contestEntry.userId.equals(userId)).collect(Collectors.toList());

            // Una vez calculado el ranking ya no estamos intesados en enviarlos al app
            contestFiltered.templateMatchEventIds = null;

            return contestFiltered;
        }).collect(Collectors.toList());

        return new ReturnHelperWithAttach()
                .attachObject("contests", result, JsonViews.MyLiveContestsV2.class)
                .toResult();
    }

    private static Result msgGetMyLiveContestV2(String userId, String contestId) throws Exception {
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
                Logger.error("WTF 7945: getViewContest: contest: {} user: {}", contestId, userId != null ? userId : "<guest>");
                return new ReturnHelper(false, ERROR_VIEW_CONTEST_INVALID).toResult();
            }

            List<UserInfo> usersInfoInContest = UserInfo.findNicknamesFromContestEntries(contestCached.contestEntries);

            // Creamos un mapa con los nickNames
            Map<ObjectId, String> usersInfoMap = new HashMap<>();
            usersInfoInContest.forEach( userInfo -> usersInfoMap.put(userInfo.userId, userInfo.nickName) );

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

                contest.contestEntries.sort((ContestEntry c1, ContestEntry c2) -> {
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

    private static Result msgGetContestInfoV2(String contestId) throws Exception {
        return Cache.getOrElse("ContestInfo-".concat(contestId), () -> {
            Contest contest = Contest.findOne(contestId);
            List<UserInfo> usersInfoInContest = UserInfo.findTrueSkillFromContestEntries(contest.contestEntries);

            ImmutableMap.Builder<Object, Object> builder = ImmutableMap.builder()
                    .put("contest", contest)
                    .put("users_info", usersInfoInContest)
                    .put("prizes", Prizes.findOne(contest));

            if (contest.state.isLive() || contest.state.isHistory()) {
                List<TemplateMatchEvent> matchEvents = TemplateMatchEvent.findAll(contest.templateMatchEventIds);
                builder.put("match_events", matchEvents);
            }

            return new ReturnHelper(builder.build())
                    .toResult(JsonViews.ContestInfo.class);
        }, CACHE_CONTEST_INFO);
    }

    private static Result msgGetTemplateSoccerPlayersV2() throws Exception {
        return Cache.getOrElse("TemplateSoccerPlayersV2", () -> {
            List<TemplateSoccerPlayer> templateSoccerPlayers = TemplateSoccerPlayer.findAllTemplate();

            List<Map<String, Object>> templateSoccerPlayersList = new ArrayList<>();
            templateSoccerPlayers.forEach(template -> {
                Map<String, Object> templateSoccerPlayer = new HashMap<>();

                templateSoccerPlayer.put("_id", template.templateSoccerPlayerId.toString());
                templateSoccerPlayer.put("name", template.name);
                templateSoccerPlayer.put("templateTeamId", template.templateTeamId.toString());

                if (template.fantasyPoints != 0) {
                    templateSoccerPlayer.put("fantasyPoints", template.fantasyPoints);

                    Object competitions = template.getCompetitions();
                    if (competitions != null) {
                        templateSoccerPlayer.put("competitions", competitions);
                    }
                }

                templateSoccerPlayersList.add(templateSoccerPlayer);
            });

            return new ReturnHelper(ImmutableMap.of(
                    "template_soccer_players", templateSoccerPlayersList
            )).toResult(JsonViews.Template.class);
        }, CACHE_TEMPLATESOCCERPLAYERS);
    }

    private static Map<String, Object> msgGetTemplateSoccerPlayerInfo(String templateSoccerPlayerId) throws Exception {
        return Cache.getOrElse("TemplateSoccerPlayerInfo-".concat(templateSoccerPlayerId), () -> {
            TemplateSoccerPlayer templateSoccerPlayer = TemplateSoccerPlayer.findOne(new ObjectId(templateSoccerPlayerId));

            Set<ObjectId> templateSoccerTeamIds = new HashSet<>();

            // Añadimos el equipo en el que juega actualmente el futbolista
            templateSoccerTeamIds.add(templateSoccerPlayer.templateTeamId);

            // Añadimos los equipos CONTRA los que ha jugado el futbolista
            for (SoccerPlayerStats stats : templateSoccerPlayer.stats) {
                templateSoccerTeamIds.add(stats.opponentTeamId);
            }

            // Incluimos el próximo partido que jugará el futbolista (y sus equipos)
            TemplateMatchEvent templateMatchEvent = TemplateMatchEvent.findNextMatchEvent(templateSoccerPlayer.templateTeamId);
            if (templateMatchEvent != null) {
                templateSoccerTeamIds.add(templateMatchEvent.templateSoccerTeamAId);
                templateSoccerTeamIds.add(templateMatchEvent.templateSoccerTeamBId);
            }

            List<TemplateSoccerTeam> templateSoccerTeams = !templateSoccerTeamIds.isEmpty() ? TemplateSoccerTeam.findAll(ListUtils.asList(templateSoccerTeamIds))
                    : new ArrayList<TemplateSoccerTeam>();

            Map<String, Object> response = new HashMap<>();
            response.put("soccer_teams", templateSoccerTeams);
            response.put("soccer_player", templateSoccerPlayer);
            if (templateMatchEvent != null) {
                response.put("match_event", templateMatchEvent);
            }
            return response;
        }, CACHE_TEMPLATESOCCERPLAYER);
    }

    private static Result msgGetTemplateSoccerTeams() throws Exception {
        return Cache.getOrElse("TemplateSoccerTeams", () -> {
            ImmutableMap.Builder<Object, Object> builder = ImmutableMap.builder()
                    .put("template_soccer_teams", TemplateSoccerTeam.findAll());
            return new ReturnHelper(builder.build())
                    .toResult(JsonViews.Template.class);
        }, CACHE_TEMPLATESOCCERTEAMS);
    }

    private static Result msgGetSoccerPlayersByCompetition(String competitionId) throws Exception {
        return Cache.getOrElse("SoccerPlayersByCompetition-".concat(competitionId), () -> {
            List<TemplateSoccerTeam> templateSoccerTeamList = TemplateSoccerTeam.findAllByCompetition(competitionId);

            List<TemplateSoccerPlayer> templateSoccerPlayers = new ArrayList<>();
            for (TemplateSoccerTeam templateSoccerTeam : templateSoccerTeamList) {
                templateSoccerPlayers.addAll(templateSoccerTeam.getTemplateSoccerPlayersWithSalary());
            }

            List<InstanceSoccerPlayer> instanceSoccerPlayers = new ArrayList<>();
            for (TemplateSoccerPlayer templateSoccerPlayer : templateSoccerPlayers) {
                instanceSoccerPlayers.add( new InstanceSoccerPlayer(templateSoccerPlayer) );
            }

            ImmutableMap.Builder<Object, Object> builder = ImmutableMap.builder()
                    .put("instanceSoccerPlayers", instanceSoccerPlayers)
                    .put("soccer_teams", templateSoccerTeamList);

            /*
            if (theUser != null) {
                builder.put("profile", theUser.getProfile());
            }
            */

            return new ReturnHelper(builder.build())
                    .toResult(JsonViews.FullContest.class);            }, CACHE_SOCCERPLAYER_BY_COMPETITION);
    }

    private static Boolean msgExistsContestInLive() throws Exception {
        return Cache.getOrElse("ExistsContestInLive", () -> TemplateContest.existsAnyInState(ContestState.LIVE), CACHE_EXISTS_LIVE);
    }

    private static List<UserRanking>  msgGetUserRankingList(String userId) throws Exception {
        List<UserRanking> userRankingList = Cache.getOrElse("LeaderboardV2", QueryActor::getSortedUsersRanking, CACHE_LEADERBOARD);

        User theUser = (userId != null && ObjectId.isValid(userId))
                ? User.findOne(new ObjectId(userId), "{ earnedMoney: 1, trueSkill: 1 }")
                : null;

        if (theUser != null) {
            // Comprobar si coinciden los datos de la caché con los del usuario que los solicita

            // Asumimos que los datos son incorrectos, dado que el usuario podría no estar en la lista cacheada
            boolean validCache = false;

            for (UserRanking ranking : userRankingList) {
                if (ranking.getUserId().equals(userId)) {
                    // Si lo que tenemos en la cache tiene los mismos datos que los que tiene actualmente el usuario
                    // consideraremos a la caché válida
                    validCache = ranking.getEarnedMoney().equals(theUser.earnedMoney)
                            && (ranking.getTrueSkill() == theUser.trueSkill);
                    break;
                }
            }

            // Hay que reconstruir la caché?
            if (!validCache) {
                Logger.debug("getLeaderboardV2: cache INVALID");

                userRankingList = getSortedUsersRanking();
                Cache.set("LeaderboardV2", userRankingList);
            } else {
                Logger.debug("getLeaderboardV2: cache OK");
            }
        }

        return userRankingList;
    }

    private static List<UserRanking> getSortedUsersRanking() {
        List<UserRanking> usersRanking = new ArrayList<UserRanking>();

        List<User> users = User.findAll(JsonViews.Leaderboard.class);

        // Creamos la estructura de ranking de usuarios
        users.forEach(user -> usersRanking.add( new UserRanking(user) ) );

        // Obtenemos la posición de ranking de cada uno de los usuarios
        class UserValue {
            public int index = 0;       // índice en la tabla general de "users"
            public float value = 0;      // valor a comparar (gold o trueskill)
            public UserValue(int index, float value) {
                this.index = index;
                this.value = value;
            }
        }

        // Crear 2 lista para obtener el ranking de trueskill y gold
        List<UserValue> skillRanking = new ArrayList<>(users.size());
        List<UserValue> goldRanking = new ArrayList<>(users.size());
        for (int i=0; i<users.size(); i++) {
            User user = users.get(i);
            skillRanking.add( new UserValue(i, user.trueSkill) );
            goldRanking.add( new UserValue(i, user.earnedMoney.getAmount().floatValue()) );
        }

        skillRanking.sort((v1, v2) -> Float.compare(v2.value, v1.value));
        goldRanking.sort((v1, v2) -> Float.compare(v2.value, v1.value));

        // Registrar el ranking en la lista de ranking de usuarios
        for (int i=0; i<users.size(); i++) {
            UserValue skillRank = skillRanking.get(i);
            usersRanking.get(skillRank.index).put("skillRank", i+1);

            UserValue goldRank = goldRanking.get(i);
            usersRanking.get(goldRank.index).put("goldRank", i+1);
        }

        return usersRanking;
    }

    private static Map<String, Object> findActiveTemplateContests() {
        List<TemplateContest> templateContests = TemplateContest.findAllActiveOrLive();
        List<TemplateMatchEvent> matchEvents = TemplateMatchEvent.gatherFromTemplateContests(templateContests);
        return ImmutableMap.of(
                "template_contests", templateContests,
                "match_events", matchEvents
        );
    }
}