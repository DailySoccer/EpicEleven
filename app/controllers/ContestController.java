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
    public static Result getUserContests() {
        long startTime = System.currentTimeMillis();

        User theUser = utils.SessionUtils.getUserFromRequest(request());

        // Obtenermos la lista de Contest Entries que el usuario ha creado y sus joins adicionales
        List<ContestEntry> contestEntries = ContestEntry.findAllForUser(theUser.userId);
        List<Contest> contests = Contest.findAllFromContestEntries(contestEntries);
        List<TemplateContest> templateContests = TemplateContest.findAllFromContests(contests);

        // Necesitamos devolver los partidos asociados a estos concursos
        List<TemplateMatchEvent> templateMatchEvents = TemplateMatchEvent.gatherFromTemplateContests(templateContests);

        Logger.info("getUserContests: {}", System.currentTimeMillis() - startTime);

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

        /*
        // Obtener el User de la session
        User theUser = utils.SessionUtils.getUserFromRequest(request());
        */

        // Obtenemos el contest
        Contest contest = Contest.findOne(contestId);

        // Obtenermos los contest Entry
        Iterable<ContestEntry> contestEntryRestuls = Model.contestEntries().find("{contestId: #}", contest.contestId).as(ContestEntry.class);
        List<ContestEntry> contestEntries = ListUtils.asList(contestEntryRestuls);

        // Obtener la informacion de los usuarios que participan en el contest
        List<UserInfo> usersInfoInContest = new ArrayList<>();
        Iterable<User> users = User.find(contestEntries).as(User.class);
        for (User user : users) {
            usersInfoInContest.add(user.info());
        }

        // Obtenemos el templateContest
        TemplateContest templateContest = TemplateContest.findOne(contest.templateContestId);

        // Obtener los match Events
        Iterable<TemplateMatchEvent> matchEvents = Model.findObjectIds(Model.templateMatchEvents(), "_id", templateContest.templateMatchEventIds).as(TemplateMatchEvent.class);

        HashMap<String, Object> content = new HashMap<>();

        content.put("contest", contest);
        content.put("users_info", usersInfoInContest);
        content.put("contest_entries", contestEntries);
        content.put("template_contest", templateContest);
        content.put("match_events", matchEvents);

        Logger.info("getContest: {}", System.currentTimeMillis() - startTime);

        return new ReturnHelper(content).toResult();
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

            // Obtener el User de la session
            User theUser = utils.SessionUtils.getUserFromRequest(request());
            if (theUser == null) {
                contestEntryForm.reject("userId", "User invalid");
            }

            // Obtener el contestId : ObjectId
            Contest aContest = Contest.findOne(params.contestId);
            if (aContest == null) {
                contestEntryForm.reject("contestId", "Contest invalid");
            }

            // Obtener los soccerIds de los futbolistas : List<ObjectId>
            List<ObjectId> soccerIds = new ArrayList<>();

            List<ObjectId> idsList = ListUtils.objectIdListFromJson(params.soccerTeam);
            Iterable<TemplateSoccerPlayer> soccers = TemplateSoccerPlayer.findAll("_id", idsList);

            String soccerNames = "";    // Requerido para Logger.info
            for (TemplateSoccerPlayer soccer : soccers) {
                soccerNames += soccer.name + " / ";
                soccerIds.add(soccer.templateSoccerPlayerId);
            }
            // Si no hemos podido encontrar todos los futbolistas referenciados por el contest entry
            if (soccerIds.size() != idsList.size()) {
                contestEntryForm.reject("contestId", "SoccerTeam invalid");
            }

            if (!contestEntryForm.hasErrors()) {
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

        User theUser = utils.SessionUtils.getUserFromRequest(request());

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

        /*
        // Obtener el User de la session
        User theUser = utils.SessionUtils.getUserFromRequest(request());
        */

        // Obtenemos el contest
        Contest contest = Contest.findOne(contestId);

        // Obtenermos los contest Entry
        Iterable<ContestEntry> contestEntryRestuls = Model.contestEntries().find("{contestId: #}", contest.contestId).as(ContestEntry.class);
        List<ContestEntry> contestEntries = ListUtils.asList(contestEntryRestuls);

        // Obtener la informacion de los usuarios que participan en el contest
        List<UserInfo> usersInfoInContest = new ArrayList<>();
        Iterable<User> users = User.find(contestEntries).as(User.class);
        for (User user : users) {
            usersInfoInContest.add(user.info());
        }

        // Obtenemos el templateContest
        TemplateContest templateContest = TemplateContest.findOne(contest.templateContestId);

        // Obtener los live Match Events
        Iterable<TemplateMatchEvent> liveMatchEvents = Model.findObjectIds(Model.liveMatchEvents(), "_id", templateContest.templateMatchEventIds).as(TemplateMatchEvent.class);

        HashMap<String, Object> content = new HashMap<>();

        content.put("contest", contest);
        content.put("users_info", usersInfoInContest);
        content.put("contest_entries", contestEntries);
        content.put("template_contest", templateContest);
        content.put("live_match_events", liveMatchEvents);

        Logger.info("getLiveContest: {}", System.currentTimeMillis() - startTime);

        return new ReturnHelper(content).toResult();
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

        if (!ObjectId.isValid(templateContestId)) {
            return new ReturnHelper(false, "TemplateContest invalid").toResult();
        }

        // Obtenemos el TemplateContest
        TemplateContest templateContest = Model.templateContests().findOne("{ _id: # }",
                new ObjectId(templateContestId)).as(TemplateContest.class);

        if (templateContest == null) {
            return new ReturnHelper(false, "TemplateContest not found").toResult();
        }

        // Consultar por los partidos del TemplateContest (queremos su version "live")
        Iterable<LiveMatchEvent> liveMatchEventResults = LiveMatchEvent.find("templateMatchEventId", templateContest.templateMatchEventIds);
        List<LiveMatchEvent> liveMatchEventList = ListUtils.asList(liveMatchEventResults);

        Logger.info("END: getLiveMatchEventsFromTemplateContest: {}", System.currentTimeMillis() - startTime);

        return new ReturnHelper(liveMatchEventList).toResult();
    }

}
