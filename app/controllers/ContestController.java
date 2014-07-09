package controllers;

import actions.AllowCors;
import actions.UserAuthenticated;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import model.*;
import org.bson.types.ObjectId;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import play.Logger;
import play.data.Form;
import play.data.validation.Constraints;
import play.mvc.Controller;
import play.mvc.Result;
import utils.ListUtils;
import utils.ReturnHelper;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;

import static play.data.Form.form;

//@UserAuthenticated
@AllowCors.Origin
public class ContestController extends Controller {

    /*
     * Devuelve la lista de contests activos (aquellos a los que un usuario puede apuntarse)
     *  Incluye los datos referenciados por cada uno de los contest (sus templates y sus match events)
     *      de forma que el cliente tenga todos los datos referenciados por un determinado contest
     *
     * TODO: ¿De donde obtendremos las fechas con las que filtar los contests? (las fechas que determinan que algo sea "activo")
     */
    public static Result getActiveContests() {
        // User theUser = (User)ctx().args.get("User");

        long startTime = System.currentTimeMillis();

        HashMap<String, Object> content = new HashMap<>();

        // Obtenemos la lista de Template Contests activos
        Iterable<TemplateContest> templateContestResults = Model.templateContests().find("{state: \"ACTIVE\"}").as(TemplateContest.class);
        List<TemplateContest> templateContests = ListUtils.asList(templateContestResults);

        // Contests <- Template Contests
        Iterable<Contest> contestsResults = Contest.find(templateContests).as(Contest.class);
        List<Contest> contests = ListUtils.asList(contestsResults);

        // Match Events <- Template Contests
        Iterable<TemplateMatchEvent> templateMatchEventsResults = TemplateMatchEvent.find(templateContests).as(TemplateMatchEvent.class);

        content.put("match_events", templateMatchEventsResults);
        content.put("template_contests", templateContests);
        content.put("contests", contests);

        // TODO: Determinar mediante la fecha qué documentos enviar
        /*
        Date startDate = new DateTime(2014, 10, 14, 12, 0, DateTimeZone.UTC).toDate();
        content.put("match_events", Model.templateMatchEvents().find("{startDate: #}", startDate).as(TemplateMatchEvent.class));
        content.put("template_contests", Model.templateContests().find("{startDate: #}", startDate).as(TemplateContest.class));
        content.put("contests", contests);
        */

        Logger.info("getActiveContests: {}", System.currentTimeMillis() - startTime);

        return new ReturnHelper(content).toResult();
    }

    @UserAuthenticated
    public static Result getUserContests() {
        long startTime = System.currentTimeMillis();

        // Obtener el User de la session
        User theUser = utils.SessionUtils.getUserFromRequest(request());

        // Averiguar la lista de Live Contests en los que el usuario participa
        List<ObjectId> contestIds = new ArrayList<>();

        // Obtenermos la lista de Contest Entries que el usuario ha creado
        Iterable<ContestEntry> contestEntries = Model.contestEntries().find("{userId: #}", theUser.userId).as(ContestEntry.class);
        for (ContestEntry contestEntry : contestEntries) {
            contestIds.add(contestEntry.contestId);
        }

        Iterable<Contest> contestResults = Model.findObjectIds(Model.contests(), "_id", contestIds).as(Contest.class);
        List<Contest> contests = ListUtils.asList(contestResults);

        Iterable<TemplateContest> templateContestResults = TemplateContest.find(contests).as(TemplateContest.class);
        List<TemplateContest> templateContests = ListUtils.asList(templateContestResults);

        Iterable<TemplateMatchEvent> matchEvents = TemplateMatchEvent.find(templateContests).as(TemplateMatchEvent.class);

        HashMap<String, Object> content = new HashMap<>();

        content.put("match_events", matchEvents);
        content.put("template_contests", templateContests);
        content.put("contests", contests);

        Logger.info("getUserContests: {}", System.currentTimeMillis() - startTime);

        return new ReturnHelper(content).toResult();
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
        Contest contest = Contest.find(contestId);

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
        TemplateContest templateContest = TemplateContest.find(contest.templateContestId);

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
            Contest aContest = Contest.find(params.contestId);
            if (aContest == null) {
                contestEntryForm.reject("contestId", "Contest invalid");
            }

            // Obtener los soccerIds de los futbolistas : List<ObjectId>
            List<ObjectId> soccerIds = new ArrayList<>();

            List<ObjectId> idsList = ListUtils.objectIdListFromJson(params.soccerTeam);
            Iterable<TemplateSoccerPlayer> soccers = TemplateSoccerPlayer.find("_id", idsList);

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

        // Obtener el User de la session
        User theUser = utils.SessionUtils.getUserFromRequest(request());

        // Averiguar la lista de Live Contests en los que el usuario participa
        List<ObjectId> contestIds = new ArrayList<>();

        // Obtenermos la lista de Contest Entries que el usuario ha creado
        Iterable<ContestEntry> contestEntries = Model.contestEntries().find("{userId: #}", theUser.userId).as(ContestEntry.class);
        for (ContestEntry contestEntry : contestEntries) {
            contestIds.add(contestEntry.contestId);
        }

        Iterable<Contest> liveContestResults = Model.findObjectIds(Model.contests(), "_id", contestIds).as(Contest.class);
        List<Contest> liveContests = ListUtils.asList(liveContestResults);

        Iterable<TemplateContest> liveTemplateContestResults = TemplateContest.find(liveContests).as(TemplateContest.class);
        List<TemplateContest> liveTemplateContests = ListUtils.asList(liveTemplateContestResults);

        Iterable<TemplateMatchEvent> liveMatchEvents = TemplateMatchEvent.find(liveTemplateContests).as(TemplateMatchEvent.class);

        HashMap<String, Object> content = new HashMap<>();

        content.put("match_events", liveMatchEvents);
        content.put("template_contests", liveTemplateContests);
        content.put("live_contests", liveContests);

        Logger.info("getLiveContests: {}", System.currentTimeMillis() - startTime);

        return new ReturnHelper(content).toResult();
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
        Contest contest = Contest.find(contestId);

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
        TemplateContest templateContest = TemplateContest.find(contest.templateContestId);

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

    // https://github.com/playframework/playframework/tree/master/samples/java/forms
    public static class MatchEventsListParams {
        @Constraints.Required
        public String matchEvents;
    }

    /**
     * Obtener los partidos "live" correspondientes a una lista de partidos (incluidos como POST.matchEvents)
     *  Incluye los fantasy points obtenidos por cada uno de los futbolistas
     *  Nota: Valido si queremos que el cliente haga querys mas especificas ("quiero estos partidos")
     *      en lugar de mas genericas ("quiero este contest")
     *      (dado que tiene todos los detalles para realizarlas con facilidad)
     * @return La lista de partidos "live"
     */
    @UserAuthenticated
    public static Result getLiveMatchEventsFromMatchEvents() {
        Form<MatchEventsListParams> matchEventForm = form(MatchEventsListParams.class).bindFromRequest();
        if (matchEventForm.hasErrors()) {
            return new ReturnHelper(false, "Form has errors").toResult();
        }

        MatchEventsListParams params = matchEventForm.get();

        Logger.info("getLiveMatchEventsFromMatchEvents: {}", params);

        long startTime = System.currentTimeMillis();

        // Convertir las strings en ObjectId
        List<ObjectId> idsList = ListUtils.objectIdListFromJson(params.matchEvents);

        Iterable<LiveMatchEvent> liveMatchEventResults = LiveMatchEvent.find("templateMatchEventId", idsList);
        List<LiveMatchEvent> liveMatchEventList = ListUtils.asList(liveMatchEventResults);

        Logger.info("END: getLiveMatchEventsFromMatchEvents: {}", System.currentTimeMillis() - startTime);

        return new ReturnHelper(liveMatchEventList).toResult();
    }

    /**
     * Obtener la lista de entradas a un contest determinado
     *  En principio, un usuario al visitar el "live" deberia solicitar dicha informacion
     *      para conocer quienes son los usuarios contrincantes y los equipos de futbolistas que seleccionaron
     * @param contest Contest en el que estamos interesados
     * @return Lista de contest entry  (incluye usuarios y equipos de futbolistas seleccionados = fantasy team)
     */
    @UserAuthenticated
    public static Result getLiveContestEntries(String contest) {
        Logger.info("getLiveContestEntries: {}", contest);

        if (!ObjectId.isValid(contest)) {
            return new ReturnHelper(false, "Contest invalid").toResult();
        }

        HashMap<String, Object> content = new HashMap<>();

        // Obtener los contest entries
        ObjectId contestId = new ObjectId(contest);
        Iterable<ContestEntry> contestEntriesResults = Model.contestEntries().find("{contestId: #}", contestId).as(ContestEntry.class);
        List<ContestEntry> contestEntriesList = ListUtils.asList(contestEntriesResults);

        // Obtener la informacion de los usuarios que participan en los contests
        List<UserInfo> usersInfoInContest = new ArrayList<>();

        List<ObjectId> userIds = new ArrayList<>();
        for (ContestEntry contestEntry : contestEntriesList) {
            userIds.add(contestEntry.userId);
        }
        Iterable<User> users = Model.findObjectIds(Model.users(), "_id", userIds).as(User.class);
        for (User user : users) {
            usersInfoInContest.add(user.info());
        }

        content.put("users_info", usersInfoInContest);
        content.put("contest_entries", contestEntriesList);

        return new ReturnHelper(content).toResult();
    }

    /**
     * Actualizar los puntos obtenidos por todos los futbolistas que participan en unos partidos (POST.matchEvents)
     *  TEMPORAL: Usado para realizar tests.
     * @return Ok
     */
    public static Result updateLiveFantasyPoints() {
        Form<MatchEventsListParams> matchEventForm = form(MatchEventsListParams.class).bindFromRequest();
        if (matchEventForm.hasErrors()) {
            return new ReturnHelper(false, "Form has errors").toResult();
        }

        MatchEventsListParams params = matchEventForm.get();

        // Convertir las stringsIds de partidos en ObjectId (de mongoDB)
        List<ObjectId> idsList = ListUtils.objectIdListFromJson(params.matchEvents);

        LiveMatchEvent.updateLiveFantasyPoints(idsList);

        return ok();
    }
}
