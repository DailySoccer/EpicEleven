package controllers;

import actions.AllowCors;
import actions.UserAuthenticated;
import com.google.common.collect.ImmutableMap;
import model.*;
import org.bson.types.ObjectId;
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

    /*
     * Devuelve la lista de contests activos (aquellos a los que un usuario puede apuntarse)
     */
    // @Cached(key = "ActiveContest", duration = 1)
    public static Result getActiveContests() {
        List<Contest> contests = Contest.findAllActiveNotFull(JsonViews.ActiveContests.class);
        return new ReturnHelper(ImmutableMap.of("contests", contests)).toResult();
    }

    @UserAuthenticated
    public static Result getMyContests() {
        User theUser = (User)ctx().args.get("User");

        List<Contest> myActiveContests = Contest.findAllMyActive(theUser.userId, JsonViews.MyActiveContests.class);
        List<Contest> myLiveContests = Contest.findAllMyLive(theUser.userId, JsonViews.MyLiveContests.class);
        List<Contest> myHistoryContests = Contest.findAllMyHistory(theUser.userId, JsonViews.MyHistoryContests.class);

        List<TemplateMatchEvent> liveMatchEvents = TemplateMatchEvent.gatherFromContests(myLiveContests);
        List<TemplateSoccerTeam> teams = TemplateSoccerTeam.findAllFromMatchEvents(liveMatchEvents);

        // Buscar todos los players que han sido incrustados en los contests
        Set<ObjectId> playersInContests = new HashSet<>();
        for (Contest liveContest: myLiveContests) {
            for (InstanceSoccerPlayer instance: liveContest.instanceSoccerPlayers) {
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
                .attachObject("soccer_players", players, JsonViews.Public.class)
                .toResult();
    }

    @UserAuthenticated
    public static Result getFullContest(String contestId) {
        // User theUser = (User)ctx().args.get("User");
        return attachInfoToContest(Contest.findOne(contestId)).toResult(JsonViews.FullContest.class);
    }

    @UserAuthenticated
    public static Result getMyContest(String contestId) {
        User theUser = (User)ctx().args.get("User");
        Contest contest = Contest.findOne(contestId);

        // Quitamos todos fantasyTeam de los contestEntries que no sean del User
        for (ContestEntry contestEntry: contest.contestEntries) {
            if (!contestEntry.userId.equals(theUser.userId)) {
                contestEntry.soccerIds.clear();
            }
        }

        return attachInfoToContest(contest).toResult(JsonViews.FullContest.class);
    }

    public static Result getPublicContest(String contestId) {
        return attachInfoToContest(Contest.findOne(contestId)).toResult(JsonViews.Extended.class);
    }

    private static ReturnHelper attachInfoToContest(Contest contest) {
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
    public static Result getLiveMatchEventsFromTemplateContest(String templateContestId) {

        // Obtenemos el TemplateContest
        TemplateContest templateContest = TemplateContest.findOne(templateContestId);

        if (templateContest == null) {
            return new ReturnHelper(false, "TemplateContest invalid").toResult();
        }

        // Consultar por los partidos del TemplateContest (queremos su version "live")
        List<TemplateMatchEvent> liveMatchEventList = TemplateMatchEvent.findAllPlaying(templateContest.templateMatchEventIds);

        return new ReturnHelper(liveMatchEventList).toResult(JsonViews.FullContest.class);
    }
}
