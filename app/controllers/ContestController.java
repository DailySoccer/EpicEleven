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
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

import static play.data.Form.form;


@AllowCors.Origin
public class ContestController extends Controller {

    /*
     * Devuelve la lista de contests activos (aquellos a los que un usuario puede apuntarse)
     * Incluye los datos referenciados por cada uno de los contest (sus templates y sus match events)
     * de forma que el cliente tenga todos los datos referenciados por un determinado contest
     */
    public static Result getActiveContests() {
        long startTime = System.currentTimeMillis();

        // Obtenemos la lista de TemplateContests activos
        List<TemplateContest> templateContests = TemplateContest.findAllActive();

        // Tambien necesitamos devolver todos los concursos instancias asociados a los templates
        List<Contest> contests = Contest.findAllFromTemplateContests(templateContests);

        // Todos los partidos asociados a todos los TemplateContests
        List<TemplateMatchEvent> templateMatchEvents = TemplateMatchEvent.gatherFromTemplateContests(templateContests);

        Logger.info("getActiveContests: {}", System.currentTimeMillis() - startTime);

        return new ReturnHelper(ImmutableMap.of("match_events", templateMatchEvents,
                                                "template_contests", templateContests,
                                                "contests", contests)).toResult();
    }

    @UserAuthenticated
    public static Result getMyContests() {
        long startTime = System.currentTimeMillis();

        User theUser = (User)ctx().args.get("User");

        // Obtenermos la lista de Contest Entries que el usuario ha creado y sus joins adicionales
        List<Contest> contests = Contest.findAllFromUser(theUser.userId);
        List<TemplateContest> templateContests = TemplateContest.findAllFromContests(contests);

        // Necesitamos devolver los partidos asociados a estos concursos
        List<TemplateMatchEvent> templateMatchEvents = TemplateMatchEvent.gatherFromTemplateContests(templateContests);

        Logger.info("getMyContests: {}", System.currentTimeMillis() - startTime);

        return new ReturnHelper(ImmutableMap.of("match_events", templateMatchEvents,
                                                "template_contests", templateContests,
                                                "contests", contests)).toResult();
    }

    /**
     * Obtener toda la información necesaria para mostrar un Contest
     * @param contestId
     * @return
     */
    @UserAuthenticated
    public static Result getContest(String contestId) {
        long startTime = System.currentTimeMillis();

        // User theUser = (User)ctx().args.get("User");

        Contest contest = Contest.findOne(contestId);
        List<ContestEntry> contestEntries = ContestEntry.findAllFromContest(contest.contestId);
        List<UserInfo> usersInfoInContest = UserInfo.findAllFromContestEntry(contestEntries);
        TemplateContest templateContest = TemplateContest.findOne(contest.templateContestId);
        List<TemplateMatchEvent> matchEvents = TemplateMatchEvent.findAll(templateContest.templateMatchEventIds);

        Logger.info("getContest: {}", System.currentTimeMillis() - startTime);

        return new ReturnHelper(ImmutableMap.of("contest", contest,
                                                "users_info", usersInfoInContest,
                                                "contest_entries", contestEntries,
                                                "template_contest", templateContest,
                                                "match_events", matchEvents)).toResult();
    }

    // https://github.com/playframework/playframework/tree/master/samples/java/forms
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

    @UserAuthenticated
    public static Result getLiveContests() {
        long startTime = System.currentTimeMillis();

        User theUser = (User)ctx().args.get("User");

        // A partir de las contest entries del usuario, hacemos joins con el resto de colecciones
        List<ContestEntry> contestEntries = ContestEntry.findAllForUser(theUser.userId);

        // TODO: No estamos restringiendo a Live
        List<Contest> liveContests = Contest.findAllFromContestEntries(contestEntries);
        List<TemplateContest> liveTemplateContests = TemplateContest.findAllFromContests(liveContests);
        List<TemplateMatchEvent> liveMatchEvents = TemplateMatchEvent.gatherFromTemplateContests(liveTemplateContests);

        Logger.info("getLiveContests: {}", System.currentTimeMillis() - startTime);

        return new ReturnHelper(ImmutableMap.of("match_events", liveMatchEvents,
                                                "template_contests", liveTemplateContests,
                                                "contests", liveContests)).toResult();
    }

    /**
     * Obtener toda la información necesaria para mostrar un Live Contest
     * @param contestId
     * @return
     */
    @UserAuthenticated
    public static Result getLiveContest(String contestId) {
        long startTime = System.currentTimeMillis();

        // User theUser = (User)ctx().args.get("User");

        Contest contest = Contest.findOne(contestId);
        List<ContestEntry> contestEntries = ContestEntry.findAllFromContest(contest.contestId);
        List<UserInfo> usersInfoInContest = UserInfo.findAllFromContestEntry(contestEntries);
        TemplateContest templateContest = TemplateContest.findOne(contest.templateContestId);
        List<LiveMatchEvent> liveMatchEvents = LiveMatchEvent.findAllFromTemplateMatchEvents(templateContest.templateMatchEventIds);

         Logger.info("getLiveContest: {}", System.currentTimeMillis() - startTime);

        return new ReturnHelper(ImmutableMap.of("contest", contest,
                                                "users_info", usersInfoInContest,
                                                "contest_entries", contestEntries,
                                                "template_contest", templateContest,
                                                "live_match_events", liveMatchEvents)).toResult();
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

        Logger.info("getLiveMatchEventsFromTemplateContest: {}", templateContestId);

        long startTime = System.currentTimeMillis();

        // Obtenemos el TemplateContest
        TemplateContest templateContest = TemplateContest.findOne(templateContestId);

        if (templateContest == null) {
            return new ReturnHelper(false, "TemplateContest invalid").toResult();
        }

        // Consultar por los partidos del TemplateContest (queremos su version "live")
        List<LiveMatchEvent> liveMatchEventList = LiveMatchEvent.findAllFromTemplateMatchEvents(templateContest.templateMatchEventIds);

        Logger.info("END: getLiveMatchEventsFromTemplateContest: {}", System.currentTimeMillis() - startTime);

        return new ReturnHelper(liveMatchEventList).toResult();
    }

}
