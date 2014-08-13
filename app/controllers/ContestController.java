package controllers;

import actions.AllowCors;
import actions.UserAuthenticated;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;
import model.*;
import org.bson.types.ObjectId;
import play.Logger;
import play.data.Form;
import play.data.validation.Constraints;
import play.mvc.Controller;
import play.mvc.Result;
import utils.ListUtils;
import utils.ReturnHelper;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static play.data.Form.form;


@AllowCors.Origin
public class ContestController extends Controller {

    /*
     * Devuelve la lista de contests activos (aquellos a los que un usuario puede apuntarse)
     * Incluye los datos referenciados por cada uno de los contest (sus templates y sus match events)
     * de forma que el cliente tenga todos los datos referenciados por un determinado contest
     */
    public static Result getActiveContests() {
        // Obtenemos la lista de TemplateContests activos
        List<TemplateContest> templateContests = TemplateContest.findAllActive();

        // Tambien necesitamos devolver todos los concursos instancias asociados a los templates
        List<Contest> contests = Contest.findAllFromTemplateContests(templateContests);

        // Todos los partidos asociados a todos los TemplateContests
        List<MatchEvent> matchEvents = MatchEvent.gatherFromTemplateContests(templateContests);

        return new ReturnHelper(ImmutableMap.of("match_events", matchEvents,
                                                "template_contests", templateContests,
                                                "contests", contests)).toResult();
    }

    @UserAuthenticated
    public static Result getMyContests() {
        User theUser = (User)ctx().args.get("User");

        // Obtenermos la lista de Contest Entries que el usuario ha creado y sus joins adicionales
        List<Contest> contests = Contest.findAllFromUser(theUser.userId);
        List<TemplateContest> templateContests = TemplateContest.findAllFromContests(contests);

        // Necesitamos devolver los partidos asociados a estos concursos
        List<MatchEvent> matchEvents = MatchEvent.gatherFromTemplateContests(templateContests);

        return new ReturnHelper(ImmutableMap.of("match_events", matchEvents,
                                                "template_contests", templateContests,
                                                "contests", contests)).toResult();
    }

    @UserAuthenticated
    public static Result getMyNextContests() {
        User theUser = (User)ctx().args.get("User");
        return getMyContestsWithState(theUser, TemplateContest.State.ACTIVE).toResult();
    }

    @UserAuthenticated
    public static Result getMyLiveContests() {
        User theUser = (User)ctx().args.get("User");
        return getMyContestsWithState(theUser, TemplateContest.State.LIVE).toResult(JsonViews.FullContest.class);
    }

    @UserAuthenticated
    public static Result getMyHistoryContests() {
        User theUser = (User)ctx().args.get("User");
        return getMyContestsWithState(theUser, TemplateContest.State.HISTORY).toResult(JsonViews.FullContest.class);
    }

    @UserAuthenticated
    private static ReturnHelper getMyContestsWithState(User theUser, TemplateContest.State state) {
        // Obtenermos la lista de Contest Entries que el usuario ha creado y sus joins adicionales
        List<Contest> contests = Contest.findAllFromUser(theUser.userId);
        List<TemplateContest> templateContests = TemplateContest.findAllFromContests(contests);

        // Registraremos los templateContests validos (del estado deseado)
        Set<ObjectId> templateContestValids = new HashSet<>();

        // Filtrar los templateContests en el estado correcto
        List<TemplateContest> templateContestsFiltered = new ArrayList<>();
        for (TemplateContest templateContest : templateContests) {
            if (templateContest.state.equals(state)) {
                templateContestsFiltered.add(templateContest);

                // Marcarlo como valido
                templateContestValids.add(templateContest.getId());
            }
        }

        // Filtrar los Contests
        List<Contest> contestFiltered = new ArrayList<>();
        for (Contest contest : contests) {
            if (templateContestValids.contains(contest.templateContestId)) {
                contestFiltered.add(contest);
            }
        }

        // Necesitamos devolver los partidos asociados a estos concursos
        List<MatchEvent> matchEvents = MatchEvent.gatherFromTemplateContests(templateContests);

        return new ReturnHelper(ImmutableMap.of("match_events", matchEvents,
                "template_contests", templateContestsFiltered,
                "contests", contestFiltered));
    }

    /**
     * Obtener toda la información necesaria para mostrar un Contest
     */
    @UserAuthenticated
    public static Result getContest(String contestId) {
        // User theUser = (User)ctx().args.get("User");

        Contest contest = Contest.findOne(contestId);
        List<UserInfo> usersInfoInContest = UserInfo.findAllFromContestEntries(contest.contestEntries);
        TemplateContest templateContest = TemplateContest.findOne(contest.templateContestId);
        List<MatchEvent> matchEvents = MatchEvent.findAllFromTemplate(templateContest.templateMatchEventIds);

        return new ReturnHelper(ImmutableMap.of("contest", contest,
                                                "users_info", usersInfoInContest,
                                                "template_contest", templateContest,
                                                "match_events", matchEvents)).toResult(JsonViews.FullContest.class);
    }

    public static class ContestEntryParams {
        @Constraints.Required
        public String contestId;

        @Constraints.Required
        public String soccerTeam;   // JSON con la lista de futbolistas seleccionados
    }

    /**
     * Añadir un contest entry
     *      (participacion de un usuario en un contest, por medio de la seleccion de un equipo de futbolistas)
     */
    @UserAuthenticated
    public static Result addContestEntry() {
        Form<ContestEntryParams> contestEntryForm = form(ContestEntryParams.class).bindFromRequest();

        if (!contestEntryForm.hasErrors()) {
            ContestEntryParams params = contestEntryForm.get();

            Logger.info("addContestEntry: contestId({}) soccerTeam({})", params.contestId, params.soccerTeam);

            User theUser = (User)ctx().args.get("User");

            // Obtener el contestId : ObjectId
            Contest aContest = Contest.findOne(params.contestId);
            if (aContest == null) {
                contestEntryForm.reject("contestId", "Contest invalid");
            }

            // Obtener los soccerIds de los futbolistas : List<ObjectId>
            List<ObjectId> idsList = ListUtils.objectIdListFromJson(params.soccerTeam);
            List<TemplateSoccerPlayer> soccers = TemplateSoccerPlayer.findAll(idsList);
            List<ObjectId> soccerIds = ListUtils.convertToIdList(soccers);

            // Si no hemos podido encontrar todos los futbolistas referenciados por el contest entry
            if (soccerIds.size() != idsList.size()) {
                contestEntryForm.reject("contestId", "SoccerTeam invalid");
            }

            if (!contestEntryForm.hasErrors()) {
                String soccerNames = "";    // Requerido para Logger.info
                for (TemplateSoccerPlayer soccer : soccers) {
                    soccerNames += soccer.name + " / ";
                }
                Logger.info("contestEntry: User[{}] / Contest[{}] = ({}) => {}", theUser.nickName, aContest.name, soccerIds.size(), soccerNames);

                // Crear el equipo en mongoDb.contestEntryCollection
                ContestEntry.create(theUser.userId, new ObjectId(params.contestId), soccerIds);
            }
        }

        JsonNode result = contestEntryForm.errorsAsJson();

        if (!contestEntryForm.hasErrors()) {
            result = new ObjectMapper().createObjectNode().put("result", "ok");
        }
        return new ReturnHelper(!contestEntryForm.hasErrors(), result).toResult();
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
        List<MatchEvent> liveMatchEventList = MatchEvent.findAllFromTemplate(templateContest.templateMatchEventIds);

        return new ReturnHelper(liveMatchEventList).toResult(JsonViews.FullContest.class);
    }

    /**
     * Obtener la información sobre un SoccerPlayer (estadísticas,...)
     */
    public static Result getTemplateSoccerPlayerInfo(String templateSoccerPlayerId) {

        TemplateSoccerPlayer templateSoccerPlayer = TemplateSoccerPlayer.findOne(new ObjectId(templateSoccerPlayerId));
        return new ReturnHelper(templateSoccerPlayer).toResult();
        // return new ReturnHelper(ImmutableMap.of("stats", templateSoccerPlayer.stats)).toResult();
    }

    public static Result getCurrentDate() {
        return new ReturnHelper(ImmutableMap.of(
                "currentDate", GlobalDate.getCurrentDate()
        )).toResult();
    }
}
