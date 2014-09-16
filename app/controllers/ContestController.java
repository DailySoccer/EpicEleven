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
import utils.ReturnHelperWithAttach;

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

        return new ReturnHelper(ImmutableMap.of("template_contests", templateContests,
                                                "contests", contests)).toResult();
    }

    @UserAuthenticated
    public static Result getMyContests() {
        User theUser = (User)ctx().args.get("User");

        // Obtener los contests en los que esté inscrito el usuario
        List<Contest> contests = Contest.findAllFromUser(theUser.userId);
        List<TemplateContest> templateContests = TemplateContest.findAllFromContests(contests);

        // Registraremos nuestras contestEntries y las de nuestros contrarios que estén en "Live"
        List<ContestEntry> contestEntries = new ArrayList<>(contests.size());

        // Conjunto para almacenar aquellos matchEventIds que estén actualmente en "Live" (según su templateContest)
        Set<ObjectId> liveTemplateMatchEventIds = new HashSet<>();

        // Miramos qué templateContest estan en "live" o no
        for (TemplateContest templateContest : templateContests) {
            boolean isLive = templateContest.isLive();

            if (isLive) {
                liveTemplateMatchEventIds.addAll(templateContest.templateMatchEventIds);
            }

            // Buscar los contests de ese mismo template...
            for (Contest contest : contests) {
                if (contest.templateContestId.equals(templateContest.templateContestId)) {
                    if (isLive) {
                        // Añadir TODOS los contestEntries
                        contestEntries.addAll(contest.contestEntries);
                    }
                    else {
                        // Añadir NUESTRO contestEntry
                        for (ContestEntry contestEntry : contest.contestEntries) {
                            if (contestEntry.userId.equals(theUser.userId)) {
                                contestEntries.add(contestEntry);
                            }
                        }
                    }
                }
            }
        }

        // Obtenemos los partidos que son jugados por todos los templateContests
        List<MatchEvent> matchEvents = MatchEvent.gatherFromTemplateContests(templateContests);

        // Diferenciaremos entre los partidos que estén en live y los "otros" (JsonViews.Public)
        List<MatchEvent> publicMatchEvents = new ArrayList<>();
        List<MatchEvent> liveMatchEvents = new ArrayList<>();
        for (MatchEvent matchEvent : matchEvents) {
            if (liveTemplateMatchEventIds.contains(matchEvent.templateMatchEventId)) {
                liveMatchEvents.add(matchEvent);
            }
            else {
                publicMatchEvents.add(matchEvent);
            }
        }

        // Enviamos nuestras contestEntries y las de nuestros contrarios aparte (además de los partidos "live" con "liveFantasyPoints")
        return new ReturnHelperWithAttach()
                .attachObject("contest_entries", contestEntries, JsonViews.FullContest.class)
                .attachObject("match_events_0", liveMatchEvents, JsonViews.FullContest.class)
                .attachObject("match_events_1", publicMatchEvents, JsonViews.Extended.class)
                .attachObject("template_contests", templateContests, JsonViews.Extended.class)
                .attachObject("contests", contests, JsonViews.Extended.class)
                .toResult();
    }

    /**
     * Obtener toda la información necesaria para mostrar un Contest
     */
    @UserAuthenticated
    public static Result getFullContest(String contestId) {
        // User theUser = (User)ctx().args.get("User");
        return getContest(contestId).toResult(JsonViews.FullContest.class);
    }

    /**
     * Obtener la información sobre un Contest
     */
    public static Result getPublicContest(String contestId) {
        return getContest(contestId).toResult(JsonViews.Extended.class);
    }

    private static ReturnHelper getContest(String contestId) {
         Contest contest = Contest.findOne(contestId);
        List<UserInfo> usersInfoInContest = UserInfo.findAllFromContestEntries(contest.contestEntries);
        TemplateContest templateContest = TemplateContest.findOne(contest.templateContestId);
        List<MatchEvent> matchEvents = MatchEvent.findAllFromTemplate(templateContest.templateMatchEventIds);

        return new ReturnHelper(ImmutableMap.of("contest", contest,
                                                "users_info", usersInfoInContest,
                                                "template_contest", templateContest,
                                                "match_events", matchEvents));
    }

    private static final String CONTEST_ENTRY_KEY = "error";
    private static final String ERROR_CONTEST_INVALID = "ERROR_CONTEST_INVALID";
    private static final String ERROR_CONTEST_NOT_ACTIVE = "ERROR_CONTEST_NOT_ACTIVE";
    private static final String ERROR_CONTEST_FULL = "ERROR_CONTEST_FULL";
    private static final String ERROR_FANTASY_TEAM_INCOMPLETE = "ERROR_FANTASY_TEAM_INCOMPLETE";
    private static final String ERROR_SALARYCAP_INVALID = "ERROR_SALARYCAP_INVALID";
    private static final String ERROR_FORMATION_INVALID = "ERROR_FORMATION_INVALID";
    private static final String ERROR_CONTEST_ENTRY_INVALID = "ERROR_CONTEST_ENTRY_INVALID";
    private static final String ERROR_OP_UNAUTHORIZED = "ERROR_OP_UNAUTHORIZED";

    public static class AddContestEntryParams {
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
        Form<AddContestEntryParams> contestEntryForm = form(AddContestEntryParams.class).bindFromRequest();

        String contestId = "";

        if (!contestEntryForm.hasErrors()) {
            AddContestEntryParams params = contestEntryForm.get();

            Logger.info("addContestEntry: contestId({}) soccerTeam({})", params.contestId, params.soccerTeam);

            User theUser = (User) ctx().args.get("User");

            // Obtener el contestId : ObjectId
            Contest aContest = Contest.findOne(params.contestId);

            // Obtener los soccerIds de los futbolistas : List<ObjectId>
            List<ObjectId> idsList = ListUtils.objectIdListFromJson(params.soccerTeam);

            if (aContest != null) {
                // Verificar que el contest no esté lleno
                if (aContest.contestEntries.size() >= aContest.maxEntries) {
                    // Buscar otro contest de características similares
                    aContest = aContest.getSameContestWithFreeSlot();
                }
            }

            List<String> errores = validateContestEntry(aContest, idsList);
            if (errores.isEmpty()) {
                ContestEntry.create(theUser.userId, aContest.contestId, idsList);

                contestId = aContest.contestId.toString();
            } else {
                // TODO: ¿Queremos informar de los distintos errores?
                for (String error : errores) {
                    contestEntryForm.reject(CONTEST_ENTRY_KEY, error);
                }
            }
        }

        JsonNode result = contestEntryForm.errorsAsJson();

        if (!contestEntryForm.hasErrors()) {
            result = new ObjectMapper().createObjectNode().put("result", "ok").put("contestId", contestId);
        }
        return new ReturnHelper(!contestEntryForm.hasErrors(), result).toResult();
    }

    public static class EditContestEntryParams {
        @Constraints.Required
        public String contestEntryId;

        @Constraints.Required
        public String soccerTeam;   // JSON con la lista de futbolistas seleccionados
    }

    @UserAuthenticated
    public static Result editContestEntry() {
        Form<EditContestEntryParams> contestEntryForm = form(EditContestEntryParams.class).bindFromRequest();

        if (!contestEntryForm.hasErrors()) {
            EditContestEntryParams params = contestEntryForm.get();

            Logger.info("editContestEntry: contestEntryId({}) soccerTeam({})", params.contestEntryId, params.soccerTeam);

            User theUser = (User) ctx().args.get("User");

            ContestEntry contestEntry = ContestEntry.findOne(params.contestEntryId);
            if (contestEntry != null) {

                // Obtener el contestId : ObjectId
                Contest aContest = Contest.findOneFromContestEntry(contestEntry.contestEntryId);

                // Obtener los soccerIds de los futbolistas : List<ObjectId>
                List<ObjectId> idsList = ListUtils.objectIdListFromJson(params.soccerTeam);

                List<String> errores = validateContestEntry(aContest, idsList);
                if (errores.isEmpty()) {
                    ContestEntry.update(contestEntry.contestEntryId, idsList);
                } else {
                    // TODO: ¿Queremos informar de los distintos errores?
                    for (String error : errores) {
                        contestEntryForm.reject(CONTEST_ENTRY_KEY, error);
                    }
                }
            }
            else {
                contestEntryForm.reject(CONTEST_ENTRY_KEY, ERROR_CONTEST_ENTRY_INVALID);
            }
        }

        JsonNode result = contestEntryForm.errorsAsJson();

        if (!contestEntryForm.hasErrors()) {
            result = new ObjectMapper().createObjectNode().put("result", "ok");
        }
        return new ReturnHelper(!contestEntryForm.hasErrors(), result).toResult();
    }

    private static List<String> validateContestEntry (Contest contest, List<ObjectId> objectIds) {
        List<String> errores = new ArrayList<>();

        // Verificar que el contest sea válido
        if (contest == null) {
            errores.add(ERROR_CONTEST_INVALID);
        } else {
            TemplateContest templateContest = TemplateContest.findOne(contest.templateContestId);

            // Verificar que el templateContest esté activo (ni "live" ni "history")
            if (!templateContest.isActive()) {
                errores.add(ERROR_CONTEST_NOT_ACTIVE);
            }

            List<MatchEvent> matchEvents = templateContest.getMatchEvents();

            // Buscar los soccerPlayers dentro de los partidos del contest
            List<SoccerPlayer> soccerPlayers = getSoccerPlayersFromMatchEvents(objectIds, matchEvents);

            // Verificar que TODOS los futbolistas seleccionados participen en los partidos del contest
            if (objectIds.size() != soccerPlayers.size()) {
                // No hemos podido encontrar todos los futbolistas referenciados por el contest entry
                errores.add(ERROR_FANTASY_TEAM_INCOMPLETE);
            }
            else {
                // Verificar que los futbolistas no cuestan más que el salaryCap del templateContest
                if (getSalaryCap(soccerPlayers) > templateContest.salaryCap) {
                    errores.add(ERROR_SALARYCAP_INVALID);
                }

                // Verificar que todos las posiciones del team están completas
                if (!isFormationValid(soccerPlayers)) {
                    errores.add(ERROR_FORMATION_INVALID);
                }
            }
        }

        return errores;
    }

    private static List<SoccerPlayer> getSoccerPlayersFromMatchEvents(List<ObjectId> ids, List<MatchEvent> matchEvents) {
        List<SoccerPlayer> soccerPlayers = new ArrayList<>();
        for (ObjectId soccerPlayerId : ids) {
            for (MatchEvent matchEvent : matchEvents) {
                if (matchEvent.containsSoccerPlayer(soccerPlayerId)) {
                    soccerPlayers.add(matchEvent.findSoccerPlayer(soccerPlayerId));
                    break;
                }
            }
        }
        return soccerPlayers;
    }

    private static int getSalaryCap(List<SoccerPlayer> soccerPlayers) {
        int salaryCapTeam = 0;
        for (SoccerPlayer soccer : soccerPlayers) {
            salaryCapTeam += soccer.salary;
        }
        return salaryCapTeam;
    }

    private static boolean isFormationValid(List<SoccerPlayer> soccerPlayers) {
        return  (countFieldPos(FieldPos.GOALKEEPER, soccerPlayers) == 1) &&
                (countFieldPos(FieldPos.DEFENSE, soccerPlayers) == 4) &&
                (countFieldPos(FieldPos.MIDDLE, soccerPlayers) == 4) &&
                (countFieldPos(FieldPos.FORWARD, soccerPlayers) == 2);
    }

    private static int countFieldPos(FieldPos fieldPos, List<SoccerPlayer> soccerPlayers) {
        int count = 0;
        for (SoccerPlayer soccerPlayer : soccerPlayers) {
            if (soccerPlayer.fieldPos.equals(fieldPos)) {
                count++;
            }
        }
        return count;
    }

    public static class CancelContestEntryParams {
        @Constraints.Required
        public String contestEntryId;
    }

    @UserAuthenticated
    public static Result cancelContestEntry() {
        Form<CancelContestEntryParams> contestEntryForm = form(CancelContestEntryParams.class).bindFromRequest();

        if (!contestEntryForm.hasErrors()) {
            CancelContestEntryParams params = contestEntryForm.get();

            Logger.info("cancelContestEntry: contestEntryId({})", params.contestEntryId);

            User theUser = (User) ctx().args.get("User");

            // Verificar que es un contestEntry válido
            ContestEntry contestEntry = ContestEntry.findOne(params.contestEntryId);
            if (contestEntry != null) {
                // Verificar que el usuario propietario del fantasyTeam sea el mismo que lo intenta borrar
                if (!contestEntry.userId.equals(theUser.userId)) {
                    contestEntryForm.reject(CONTEST_ENTRY_KEY, ERROR_OP_UNAUTHORIZED);
                }

                Contest contest = Contest.findOneFromContestEntry(contestEntry.contestEntryId);
                TemplateContest templateContest = TemplateContest.findOne(contest.templateContestId);

                // Verificar que el contest sigue estando activo (ni "live" ni "history")
                if (!templateContest.isActive()) {
                    contestEntryForm.reject(CONTEST_ENTRY_KEY, ERROR_CONTEST_NOT_ACTIVE);
                }

                if (!contestEntryForm.hasErrors()) {
                    ContestEntry.remove(contest.contestId, contestEntry.contestEntryId);
                }
            }
            else {
                contestEntryForm.reject(CONTEST_ENTRY_KEY, ERROR_CONTEST_ENTRY_INVALID);
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
}
