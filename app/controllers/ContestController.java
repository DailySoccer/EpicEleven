package controllers;

import actions.AllowCors;
import actions.UserAuthenticated;
import com.google.common.collect.ImmutableMap;
import model.*;
import org.bson.types.ObjectId;
import play.Logger;
import play.libs.F;
import play.mvc.Controller;
import play.mvc.Result;
import utils.ListUtils;
import utils.ReturnHelper;
import utils.ReturnHelperWithAttach;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@AllowCors.Origin
public class ContestController extends Controller {

    private static final String ERROR_VIEW_CONTEST_INVALID = "ERROR_VIEW_CONTEST_INVALID";
    private static final String ERROR_MY_CONTEST_INVALID = "ERROR_MY_CONTEST_INVALID";
    private static final String ERROR_MY_CONTEST_ENTRY_INVALID = "ERROR_MY_CONTEST_ENTRY_INVALID";
    private static final String ERROR_TEMPLATE_CONTEST_INVALID = "ERROR_TEMPLATE_CONTEST_INVALID";

    /*
     * Devuelve la lista de contests activos (aquellos a los que un usuario puede apuntarse)
     */
    // @Cached(key = "ActiveContest", duration = 1)
    @UserAuthenticated
    public static F.Promise<Result> getActiveContests() {
        return F.Promise.promise(() -> _getActiveContests()).map((ReturnHelper i) -> i.toResult());
    }
    private static ReturnHelper _getActiveContests() {
        // Query que compara el "número de entries" con "maxEntries" (parece más lenta que haciendo el filtro a mano)
        // List<Contest> contests = Contest.findAllActiveNotFull(JsonViews.ActiveContests.class);
        List<Contest> contests = Contest.findAllActive(JsonViews.ActiveContests.class);

        // Filtrar los contests que ya están completos
        List<Contest> contestsNotFull = new ArrayList<>(contests.size());
        for (Contest contest: contests) {
            if (!contest.isFull()) {
                contestsNotFull.add(contest);
            }
        }

        return new ReturnHelper(ImmutableMap.of("contests", contestsNotFull));
    }



    @UserAuthenticated
    public static F.Promise<Result> getMyContests() {
        return F.Promise.promise(() -> _getMyContests()).map((ReturnHelper i) -> i.toResult());
    }
    private static ReturnHelper _getMyContests() {
        User theUser = (User) ctx().args.get("User");

        List<Contest> myActiveContests = Contest.findAllMyActive(theUser.userId, JsonViews.MyActiveContests.class);
        List<Contest> myLiveContests = Contest.findAllMyLive(theUser.userId, JsonViews.MyLiveContests.class);
        List<Contest> myHistoryContests = Contest.findAllMyHistory(theUser.userId, JsonViews.MyHistoryContests.class);

        List<TemplateMatchEvent> liveMatchEvents = TemplateMatchEvent.gatherFromContests(myLiveContests);
        List<TemplateSoccerTeam> teams = TemplateSoccerTeam.findAllFromMatchEvents(liveMatchEvents);

        // Buscar todos los players que han sido incrustados en los contests
        Set<ObjectId> playersInContests = new HashSet<>();
        for (Contest liveContest : myLiveContests) {
            for (InstanceSoccerPlayer instance : liveContest.instanceSoccerPlayers) {
                playersInContests.add(instance.templateSoccerPlayerId);
            }
        }

        List<TemplateSoccerPlayer> players = TemplateSoccerPlayer.findAll(ListUtils.asList(playersInContests.iterator()));

        return new ReturnHelperWithAttach()
                .attachObject("contests_0", myActiveContests, JsonViews.Public.class)
                .attachObject("contests_1", myLiveContests, JsonViews.FullContest.class)
                .attachObject("contests_2", myHistoryContests, JsonViews.Extended.class)
                .attachObject("match_events", liveMatchEvents, JsonViews.FullContest.class)
                .attachObject("soccer_teams", teams, JsonViews.Public.class)
                .attachObject("soccer_players", players, JsonViews.Public.class);
    }


    @UserAuthenticated
    public static F.Promise<Result> getViewContest(String contestId) {
        return F.Promise.promise(() -> _getViewContest(contestId)).map((ReturnHelper i) -> i.toResult(JsonViews.FullContest.class));
    }
    private static ReturnHelper _getViewContest(String contestId) {
        User theUser = (User)ctx().args.get("User");
        Contest contest = Contest.findOne(contestId);

        // No se puede ver el contest "completo" si está "activo" (únicamente en "live" o "history")
        if (contest.isActive()) {
            Logger.error("WTF 7945: getViewContest: contest: {} user: {}", contestId, theUser.userId);
            return new ReturnHelper(false, ERROR_VIEW_CONTEST_INVALID);
        }

        if (!contest.containsContestEntryWithUser(theUser.userId)) {
            Logger.error("WTF 7942: getViewContest: contest: {} user: {}", contestId, theUser.userId);
        }

        List<UserInfo> usersInfoInContest = UserInfo.findAllFromContestEntries(contest.contestEntries);
        List<TemplateMatchEvent> matchEvents = TemplateMatchEvent.findAll(contest.templateMatchEventIds);
        List<TemplateSoccerTeam> teams = TemplateSoccerTeam.findAllFromMatchEvents(matchEvents);

        // Buscar todos los players que han sido incrustados en los contestEntries
        Set<ObjectId> playersInContestEntries = new HashSet<>();
        for (ContestEntry contestEntry: contest.contestEntries) {
            playersInContestEntries.addAll(contestEntry.soccerIds);
        }
        List<TemplateSoccerPlayer> players = TemplateSoccerPlayer.findAll(ListUtils.asList(playersInContestEntries.iterator()));

        return new ReturnHelper(ImmutableMap.builder()
                .put("contest", contest)
                .put("users_info", usersInfoInContest)
                .put("match_events", matchEvents)
                .put("soccer_teams", teams)
                .put("soccer_players", players)
                .put("prizes", Prizes.findOne(contest))
                .build());
    }

    @UserAuthenticated
    public static F.Promise<Result> getMyContest(String contestId) {
        return F.Promise.promise(() -> _getMyContest(contestId)).map((ReturnHelper i) -> i.toResult(JsonViews.FullContest.class));
    }
    private static ReturnHelper _getMyContest(String contestId) {
        User theUser = (User)ctx().args.get("User");
        Contest contest = Contest.findOne(contestId);
        if (!contest.containsContestEntryWithUser(theUser.userId)) {
            Logger.error("WTF 7943: getMyContest: contest: {} user: {}", contestId, theUser.userId);
            return new ReturnHelper(false, ERROR_MY_CONTEST_INVALID);
        }

        // Quitamos todos fantasyTeam de los contestEntries que no sean del User
        for (ContestEntry contestEntry: contest.contestEntries) {
            if (!contestEntry.userId.equals(theUser.userId)) {
                contestEntry.soccerIds.clear();
            }
        }

        return _attachInfoToContest(contest);
    }

    @UserAuthenticated
    public static F.Promise<Result> getMyContestEntry(String contestId) {
        return F.Promise.promise(() -> _getMyContestEntry(contestId)).map((ReturnHelper i) -> i.toResult(JsonViews.FullContest.class));
    }
    private static ReturnHelper _getMyContestEntry(String contestId) {
        User theUser = (User)ctx().args.get("User");
        Contest contest = Contest.findOne(contestId);
        if (!contest.containsContestEntryWithUser(theUser.userId)) {
            Logger.error("WTF 7944: getMyContestEntry: contest: {} user: {}", contestId, theUser.userId);
            return new ReturnHelper(false, ERROR_MY_CONTEST_ENTRY_INVALID);
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
        List<TemplateSoccerTeam> teams = TemplateSoccerTeam.findAllFromMatchEvents(matchEvents);

        List<TemplateSoccerPlayer> players = TemplateSoccerPlayer.findAll(ListUtils.asList(playersInContestEntry.iterator()));

        return new ReturnHelper(ImmutableMap.of("contest", contest,
                "users_info", usersInfoInContest,
                "match_events", matchEvents,
                "soccer_teams", teams,
                "soccer_players", players));
    }

    public static F.Promise<Result> getContestInfo(String contestId) {
        return F.Promise.promise(() -> _getContestInfo(contestId)).map((ReturnHelper i) -> i.toResult(JsonViews.ContestInfo.class));
    }
    private static ReturnHelper _getContestInfo(String contestId) {
        Contest contest = Contest.findOne(contestId);
        List<UserInfo> usersInfoInContest = UserInfo.findAllFromContestEntries(contest.contestEntries);
        List<TemplateMatchEvent> matchEvents = TemplateMatchEvent.findAll(contest.templateMatchEventIds);
        List<TemplateSoccerTeam> teams = TemplateSoccerTeam.findAllFromMatchEvents(matchEvents);

        return new ReturnHelper(ImmutableMap.of(
                "contest", contest,
                "users_info", usersInfoInContest,
                "match_events", matchEvents,
                "soccer_teams", teams,
                "prizes", Prizes.findOne(contest)));
    }


    public static F.Promise<Result> getPublicContest(String contestId) {
        return F.Promise.promise(() -> _attachInfoToContest(Contest.findOne(contestId))).map((ReturnHelper i) -> i.toResult(JsonViews.Extended.class));
    }
    private static ReturnHelper _attachInfoToContest(Contest contest) {
        List<UserInfo> usersInfoInContest = UserInfo.findAllFromContestEntries(contest.contestEntries);
        List<TemplateMatchEvent> matchEvents = TemplateMatchEvent.findAll(contest.templateMatchEventIds);
        List<TemplateSoccerTeam> teams = TemplateSoccerTeam.findAllFromMatchEvents(matchEvents);

        // Buscar todos los players que han sido incrustados en los contests
        Set<ObjectId> playersInContests = new HashSet<>();
        for (InstanceSoccerPlayer instance: contest.instanceSoccerPlayers) {
            playersInContests.add(instance.templateSoccerPlayerId);
        }
        List<TemplateSoccerPlayer> players = TemplateSoccerPlayer.findAll(ListUtils.asList(playersInContests.iterator()));

        return new ReturnHelper(ImmutableMap.of("contest", contest,
                                                "users_info", usersInfoInContest,
                                                "match_events", matchEvents,
                                                "soccer_teams", teams,
                                                "soccer_players", players));
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
    @UserAuthenticated
    public static F.Promise<Result> getLiveMatchEventsFromTemplateContest(String templateContestId) {
        return F.Promise.promise(() -> _getLiveMatchEventsFromTemplateContest(templateContestId)).map((ReturnHelper i) -> i.toResult(JsonViews.FullContest.class));
    }
    private static ReturnHelper _getLiveMatchEventsFromTemplateContest(String templateContestId) {
        // Obtenemos el TemplateContest
        TemplateContest templateContest = TemplateContest.findOne(templateContestId);

        if (templateContest == null) {
            return new ReturnHelper(false, ERROR_TEMPLATE_CONTEST_INVALID);
        }

        // Consultar por los partidos del TemplateContest (queremos su version "live")
        List<TemplateMatchEvent> liveMatchEventList = TemplateMatchEvent.findAllPlaying(templateContest.templateMatchEventIds);

        return new ReturnHelper(liveMatchEventList);
    }
}
