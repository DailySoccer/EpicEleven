package controllers;

import actions.AllowCors;
import model.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import play.Logger;
import play.data.Form;
import play.data.validation.Constraints;
import play.libs.Json;
import play.mvc.Controller;
import play.mvc.Result;
import utils.*;

import java.util.Date;
import java.util.HashMap;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import com.mongodb.MongoException;

import static play.data.Form.form;

import org.bson.types.ObjectId;
import java.util.List;
import java.util.ArrayList;

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

        Date startDate = new DateTime(2014, 10, 14, 12, 0, DateTimeZone.UTC).toDate();

        HashMap<String, Object> contest = new HashMap<>();

        contest.put("match_events", Model.templateMatchEvents().find("{startDate: #}", startDate).as(TemplateMatchEvent.class));
        contest.put("template_contests", Model.templateContests().find("{startDate: #}", startDate).as(TemplateContest.class));
        contest.put("contests", Model.contests().find().as(Contest.class));

        // Logger.info("getActiveContests: {}", System.currentTimeMillis() - startTime);

        return new ReturnHelper(contest).toResult();
    }

    /*
     * Devuelve la lista de partidos (match events) activos (referenciados por los contests activos)
     */
    public static Result getActiveMatchEvents() {
        // User theUser = (User)ctx().args.get("User");

        Date startDate = new DateTime(2014, 10, 14, 12, 0, DateTimeZone.UTC).toDate();
        return new ReturnHelper(Model.templateMatchEvents().find(
            //"{startDate: {$lte : #}}", startDate
            "{startDate: #}", startDate
        ).as(TemplateMatchEvent.class)).toResult();
    }

    // https://github.com/playframework/playframework/tree/master/samples/java/forms
    public static class ContestEntryParams {
        @Constraints.Required
        public String userId;

        @Constraints.Required
        public String contestId;

        @Constraints.Required
        public String soccerTeam;
    }

    /**
     * Añadir un contest entry
     *      (participacion de un usuario en un contest, por medio de la seleccion de un equipo de futbolistas)
     */
    public static Result addContestEntry() {
        Form<ContestEntryParams> contestEntryForm = form(ContestEntryParams.class).bindFromRequest();

        if (!contestEntryForm.hasErrors()) {
            ContestEntryParams params = contestEntryForm.get();

            Logger.info("addContestEntry: userId({}) contestId({}) soccerTeam({})", params.userId, params.contestId, params.soccerTeam);

            // Obtener el userId : ObjectId
            User aUser = Model.findUserId(params.userId);
            if (aUser == null) {
                contestEntryForm.reject("userId", "User invalid");
            }

            // Obtener el contestId : ObjectId
            Contest aContest = Model.findContestId(params.contestId);
            if (aContest == null) {
                contestEntryForm.reject("contestId", "Contest invalid");
            }

            // Obtener los soccerIds de los futbolistas : List<ObjectId>
            List<ObjectId> soccerIds = new ArrayList<>();

            List<ObjectId> idsList = ListUtils.objectIdListFromJson(params.soccerTeam);
            Iterable<TemplateSoccerPlayer> soccers = Model.findTemplateSoccerPlayersFromIds("_id", idsList);

            String soccerNames = "";    // Requerido para Logger.info
            for (TemplateSoccerPlayer soccer : soccers) {
                soccerNames += soccer.name + " / ";
                soccerIds.add(soccer.templateSoccerPlayerId);
            }

            if (!contestEntryForm.hasErrors()) {
                Logger.info("contestEntry: Contest[{}] / User[{}] = ({}) => {}", aContest.name, aUser.nickName, soccerIds.size(), soccerNames);

                // Crear el equipo en mongoDb.contestEntryCollection
                createContestEntry(new ObjectId(params.userId), new ObjectId(params.contestId), soccerIds);
            }
        }

        JsonNode result = contestEntryForm.errorsAsJson();

        if (!contestEntryForm.hasErrors()) {
            result = new ObjectMapper().createObjectNode().put("result", "ok");
        }
        return new ReturnHelper(!contestEntryForm.hasErrors(), result).toResult();
    }

    /**
     * Creacion de un contest entry (se añade a la base de datos)
     * @param user      Usuario al que pertenece el equipo
     * @param contest   Contest al que se apunta
     * @param soccers   Lista de futbolistas con la que se apunta
     * @return Si se ha realizado correctamente su creacion
     */
    private static boolean createContestEntry(ObjectId user, ObjectId contest, List<ObjectId> soccers) {
        boolean bRet = true;

        try {
            ContestEntry aContestEntry = new ContestEntry(user, contest, soccers);
            Model.contestEntries().insert(aContestEntry);
        } catch (MongoException exc) {
            Logger.error("createContestEntry: ", exc);
            bRet = false;
        }

        return bRet;
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
        Iterable<LiveMatchEvent> liveMatchEventResults = Model.findLiveMatchEventsFromIds("templateMatchEventId", templateContest.templateMatchEventIds);
        List<LiveMatchEvent> liveMatchEventList = ListUtils.listFromIterator(liveMatchEventResults.iterator());

        // TODO: Si no encontramos ningun LiveMatchEvent, los creamos
        if (liveMatchEventList.isEmpty()) {
            Logger.info("create liveMatchEvents from TemplateContest({})", templateContest.templateContestId);

            // Obtenemos la lista de TemplateMatchEvents correspondientes al TemplateContest
            Iterable<TemplateMatchEvent> templateMatchEventsResults = Model.findTemplateMatchEventFromIds("_id", templateContest.templateMatchEventIds);

            // Creamos un LiveMatchEvent correspondiente a un TemplateMatchEvent
            for (TemplateMatchEvent templateMatchEvent : templateMatchEventsResults) {
                LiveMatchEvent liveMatchEvent = new LiveMatchEvent(templateMatchEvent);
                // Lo insertamos en la BDD
                Model.liveMatchEvents().insert(liveMatchEvent);
                // Lo añadimos en la lista de elementos a devolver
                liveMatchEventList.add(liveMatchEvent);
            }
        }
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

        Iterable<LiveMatchEvent> liveMatchEventResults = Model.findLiveMatchEventsFromIds("templateMatchEventId", idsList);
        List<LiveMatchEvent> liveMatchEventList = ListUtils.listFromIterator(liveMatchEventResults.iterator());

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
    public static Result getLiveContestEntries(String contest) {
        Logger.info("getLiveContestEntries: {}", contest);

        if (!ObjectId.isValid(contest)) {
            return new ReturnHelper(false, "Contest invalid").toResult();
        }

        ObjectId contestId = new ObjectId(contest);
        return new ReturnHelper(Model.contestEntries().find("{contestId: #}", contestId).as(ContestEntry.class)).toResult();
    }

    /**
     * Actualizar los puntos obtenidos por un determinado futbolista
     *  TEMPORAL: Usado para realizar tests. No tiene sentido que los puntos nos vengan desde "fuera"
     * @param strSoccerPlayerId Identificador del futbolista (templateSoccerPlayerId)
     * @param strPoints Puntos (en formato cadena)
     * @return Ok
     */
    public static Result setLiveFantasyPointsOfSoccerPlayer(String strSoccerPlayerId, String strPoints) {
        if (!ObjectId.isValid(strSoccerPlayerId)) {
            return new ReturnHelper(false, "SoccerPlayer invalid").toResult();
        }

        Model.setLiveFantasyPointsOfSoccerPlayer(new ObjectId(strSoccerPlayerId), strPoints);

        return ok();
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

        Model.updateLiveFantasyPoints(idsList);

        return ok();
    }
}
