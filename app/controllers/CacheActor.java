package controllers;

import akka.actor.UntypedActor;
import com.google.common.collect.ImmutableMap;
import model.*;
import org.bson.types.ObjectId;
import play.Logger;
import play.cache.Cache;
import play.mvc.Result;
import utils.ListUtils;
import utils.ReturnHelper;

import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

public class CacheActor extends UntypedActor {

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


    public static class CacheMsg {
        public String msg;
        public String userId;
        public Object param;

        public CacheMsg(String m, String u, Object p) { msg = m; userId = u; param = p; }
        public CacheMsg(String m, Object p) { msg = m; param = p; }
    }


    public CacheActor() {}

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

        Logger.debug("CacheActor: {}: {}", msg.msg, msg.param);

        switch (msg.msg) {
            case "getSoccerPlayersByCompetition":
                String competitionId = (String) msg.param;
                sender().tell(msgGetSoccerPlayersByCompetition((String) msg.param), getSelf());
                break;

            case "getActiveContestV2":
                sender().tell(msgGetActiveContestV2((String) msg.param), getSelf());
                break;

            case "getTemplateSoccerPlayerInfo":
                sender().tell(msgGetTemplateSoccerPlayerInfo((String) msg.param), getSelf());
                break;

            default:
                unhandled(msg);
                break;
        }
    }

    private void onReceive(String msg) throws Exception {

        Logger.debug("CacheActor: {}", msg);

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
        return Cache.getOrElse("ActiveTemplateContests", new Callable<Map<String, Object>>() {
            @Override
            public Map<String, Object> call() throws Exception {
                return findActiveTemplateContests();
            }
        }, CACHE_ACTIVE_TEMPLATE_CONTESTS);
    }

    private static Map<String, Object> msgGetActiveTemplateContests() throws Exception {
        return Cache.getOrElse("ActiveTemplateContests", new Callable<Map<String, Object>>() {
            @Override
            public Map<String, Object> call() throws Exception {
                return findActiveTemplateContests();
            }
        }, CACHE_ACTIVE_TEMPLATE_CONTESTS);
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
        return Cache.getOrElse("CountActiveTemplateContests", new Callable<Result>() {
            @Override
            public Result call() throws Exception {
                return new ReturnHelper(ImmutableMap.of(
                        "count", TemplateContest.countAllActiveOrLive()
                )).toResult();
            }
        }, CACHE_COUNT_ACTIVE_TEMPLATE_CONTESTS);
    }

    private static List<Contest> msgGetActiveContestsV2() throws Exception {
        return Cache.getOrElse("ActiveContestsV2", new Callable<List<Contest>>() {
            @Override
            public List<Contest> call() throws Exception {
                return Contest.findAllActiveNotFull(JsonViews.ActiveContestsV2.class);
                //return Contest.findAllActive(JsonViews.ActiveContests.class);
            }
        }, CACHE_ACTIVE_CONTESTS);
    }

    private static Result msgGetActiveContestV2(String contestId) throws Exception {
        return Cache.getOrElse("ActiveContestV2-".concat(contestId), new Callable<Result>() {
            @Override
            public Result call() throws Exception {
                Contest contest = Contest.findOne(new ObjectId(contestId), JsonViews.ActiveContest.class);
                return new ReturnHelper(ImmutableMap.of("contest", contest)).toResult(JsonViews.ActiveContest.class);
            }
        }, CACHE_ACTIVE_CONTEST);
    }

    private static Result msgGetTemplateSoccerPlayersV2() throws Exception {
        return Cache.getOrElse("TemplateSoccerPlayersV2", new Callable<Result>() {
            @Override
            public Result call() throws Exception {
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
            }
        }, CACHE_TEMPLATESOCCERPLAYERS);
    }

    private static Map<String, Object> msgGetTemplateSoccerPlayerInfo(String templateSoccerPlayerId) throws Exception {
        return Cache.getOrElse("TemplateSoccerPlayerInfo-".concat(templateSoccerPlayerId), new Callable<Map<String, Object>>() {
            @Override
            public Map<String, Object> call() throws Exception {
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
            }
        }, CACHE_TEMPLATESOCCERPLAYER);
    }

    private static Result msgGetTemplateSoccerTeams() throws Exception {
        return Cache.getOrElse("TemplateSoccerTeams", new Callable<Result>() {
            @Override
            public Result call() throws Exception {
                ImmutableMap.Builder<Object, Object> builder = ImmutableMap.builder()
                        .put("template_soccer_teams", TemplateSoccerTeam.findAll());
                return new ReturnHelper(builder.build())
                        .toResult(JsonViews.Template.class);
            }
        }, CACHE_TEMPLATESOCCERTEAMS);
    }

    private static Result msgGetSoccerPlayersByCompetition(String competitionId) throws Exception {
        return Cache.getOrElse("SoccerPlayersByCompetition-".concat(competitionId), new Callable<Result>() {
            @Override
            public Result call() throws Exception {
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
                        .toResult(JsonViews.FullContest.class);            }
        }, CACHE_SOCCERPLAYER_BY_COMPETITION);
    }

    private static Boolean msgExistsContestInLive() throws Exception {
        return Cache.getOrElse("ExistsContestInLive", new Callable<Boolean>() {
            @Override
            public Boolean call() throws Exception {
                return TemplateContest.existsAnyInState(ContestState.LIVE);
            }
        }, CACHE_EXISTS_LIVE);
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