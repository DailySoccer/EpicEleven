package controllers;

import actions.AllowCors;
import actions.UserAuthenticated;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;
import model.*;
import model.jobs.CancelContestEntryJob;
import model.jobs.EnterContestJob;
import org.bson.types.ObjectId;
import org.joda.money.CurrencyUnit;
import org.joda.money.Money;
import play.Logger;
import play.data.Form;
import play.data.validation.Constraints;
import play.mvc.Controller;
import play.mvc.Result;
import utils.ListUtils;
import utils.MoneyUtils;
import utils.ReturnHelper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static play.data.Form.form;

@AllowCors.Origin
public class ContestEntryController extends Controller {

    private static final String CONTEST_ENTRY_KEY = "error";
    private static final String ERROR_CONTEST_INVALID = "ERROR_CONTEST_INVALID";
    private static final String ERROR_CONTEST_NOT_ACTIVE = "ERROR_CONTEST_NOT_ACTIVE";
    private static final String ERROR_CONTEST_FULL = "ERROR_CONTEST_FULL";
    private static final String ERROR_FANTASY_TEAM_INCOMPLETE = "ERROR_FANTASY_TEAM_INCOMPLETE";
    private static final String ERROR_SALARYCAP_INVALID = "ERROR_SALARYCAP_INVALID";
    private static final String ERROR_FORMATION_INVALID = "ERROR_FORMATION_INVALID";
    private static final String ERROR_CONTEST_ENTRY_INVALID = "ERROR_CONTEST_ENTRY_INVALID";
    private static final String ERROR_OP_UNAUTHORIZED = "ERROR_OP_UNAUTHORIZED";
    private static final String ERROR_USER_ALREADY_INCLUDED = "ERROR_USER_ALREADY_INCLUDED";
    private static final String ERROR_USER_BALANCE_NEGATIVE = "ERROR_USER_BALANCE_NEGATIVE";
    private static final String ERROR_MAX_PLAYERS_SAME_TEAM = "ERROR_MAX_PLAYERS_SAME_TEAM";
    private static final String ERROR_RETRY_OP = "ERROR_RETRY_OP";

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

        User theUser = (User) ctx().args.get("User");

        // Donde nos solicitan que quieren insertarlo
        String contestIdRequested = "";

        // Id del contest que hemos encontrado
        ObjectId contestIdValid = null;

        if (!contestEntryForm.hasErrors()) {
            AddContestEntryParams params = contestEntryForm.get();

            contestIdRequested = params.contestId;

            // Buscar el contest : ObjectId
            Contest aContest = Contest.findOne(contestIdRequested);

            // Obtener los soccerIds de los futbolistas : List<ObjectId>
            List<ObjectId> idsList = ListUtils.objectIdListFromJson(params.soccerTeam);

            List<String> errores = new ArrayList<>();
            if (aContest != null) {
                if (aContest.containsContestEntryWithUser(theUser.userId)) {
                    errores.add(ERROR_USER_ALREADY_INCLUDED);
                }
                // Verificar que el contest no esté lleno
                else if (aContest.contestEntries.size() >= aContest.maxEntries) {
                    // Buscar otro contest de características similares
                    aContest = aContest.getSameContestWithFreeSlot(theUser.userId);
                    if (aContest == null) {
                        // Si no encontramos ningún Contest semejante, pedimos al webClient que lo intente otra vez
                        //  dado que asumimos que simplemente es un problema "temporal"
                        errores.add(ERROR_RETRY_OP);
                    }
                }

                // Si tenemos un contest valido, registramos su ID
                if (aContest != null) {
                    contestIdValid = aContest.contestId;
                }
            }

            if (errores.isEmpty()) {
                errores = validateContestEntry(aContest, idsList);
            }

            if (errores.isEmpty()) {
                if (MoneyUtils.isGreaterThan(aContest.entryFee, Money.zero(Product.CURRENCY_DEFAULT))) {
                    // Verificar que el usuario tiene dinero suficiente...
                    Money userBalance = User.calculateBalance(theUser.userId);
                    if (MoneyUtils.compareTo(userBalance, aContest.entryFee) < 0) {
                        errores.add(ERROR_USER_BALANCE_NEGATIVE);
                    }
                }
            }
            if (errores.isEmpty()) {
                if (aContest == null) {
                    throw new RuntimeException("WTF 8639: aContest != null");
                }

                EnterContestJob enterContestJob = EnterContestJob.create(theUser.userId, contestIdValid, idsList);

                // Al intentar aplicar el job puede que nos encontremos con algún conflicto (de última hora),
                //  lo volvemos a intentar para poder informar del error (con los tests anteriores)
                if (!enterContestJob.isDone()) {
                    errores.add(ERROR_RETRY_OP);
                }
            }

            for (String error : errores) {
                contestEntryForm.reject(CONTEST_ENTRY_KEY, error);
            }
        }

        Object result = contestEntryForm.errorsAsJson();

        if (!contestEntryForm.hasErrors()) {
            // El usuario ha sido añadido en el contest que solicitó
            //   o en otro de características semejantes (al estar lleno el anterior)
            if (contestIdValid.equals(contestIdRequested)) {
                Logger.info("addContestEntry: userId: {}: contestId: {}", theUser.userId.toString(), contestIdRequested);
            }
            else {
                Logger.info("addContestEntry: userId: {}: contestId: {} => {}", theUser.userId.toString(), contestIdRequested, contestIdValid.toString());
            }

            result = ImmutableMap.of(
                    "result", "ok",
                    "contestId", contestIdValid.toString(),
                    "profile", theUser.getProfile());
        }
        else {
            Logger.warn("addContestEntry failed: userId: {}: contestId: {}: error: {}", theUser.userId.toString(), contestIdRequested, contestEntryForm.errorsAsJson());
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
                    if (!ContestEntry.update(theUser.userId, aContest.contestId, contestEntry.contestEntryId, idsList)) {
                        errores.add(ERROR_RETRY_OP);
                    }
                }

                // TODO: ¿Queremos informar de los distintos errores?
                for (String error : errores) {
                    contestEntryForm.reject(CONTEST_ENTRY_KEY, error);
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
        else {
            Logger.error("WTF 7240: editContestEntry: {}", contestEntryForm.errorsAsJson());
        }
        return new ReturnHelper(!contestEntryForm.hasErrors(), result).toResult();
    }


    public static class CancelContestEntryParams {
        @Constraints.Required
        public String contestEntryId;
    }

    @UserAuthenticated
    public static Result cancelContestEntry() {
        Form<CancelContestEntryParams> contestEntryForm = form(CancelContestEntryParams.class).bindFromRequest();

        User theUser = (User) ctx().args.get("User");

        if (!contestEntryForm.hasErrors()) {
            CancelContestEntryParams params = contestEntryForm.get();

            Logger.info("cancelContestEntry: contestEntryId({})", params.contestEntryId);

            // Verificar que es un contestEntry válido
            ContestEntry contestEntry = ContestEntry.findOne(params.contestEntryId);
            if (contestEntry != null) {
                // Verificar que el usuario propietario del fantasyTeam sea el mismo que lo intenta borrar
                if (!contestEntry.userId.equals(theUser.userId)) {
                    contestEntryForm.reject(CONTEST_ENTRY_KEY, ERROR_OP_UNAUTHORIZED);
                }

                Contest contest = Contest.findOneFromContestEntry(contestEntry.contestEntryId);

                // Verificar que el contest sigue estando activo (ni "live" ni "history")
                if (!contest.isActive()) {
                    contestEntryForm.reject(CONTEST_ENTRY_KEY, ERROR_CONTEST_NOT_ACTIVE);
                }

                if (!contestEntryForm.hasErrors()) {
                    CancelContestEntryJob cancelContestEntryJob = CancelContestEntryJob.create(theUser.userId, contest.contestId, contestEntry.contestEntryId);
                    if (!cancelContestEntryJob.isDone()) {
                        contestEntryForm.reject(ERROR_RETRY_OP);
                    }
                }
            }
            else {
                contestEntryForm.reject(CONTEST_ENTRY_KEY, ERROR_CONTEST_ENTRY_INVALID);
            }
        }

        Object result = contestEntryForm.errorsAsJson();

        if (!contestEntryForm.hasErrors()) {
            result = ImmutableMap.of(
                    "result", "ok",
                    "profile", theUser.getProfile());
        }
        else {
            Logger.error("WTF 7241: cancelContestEntry: {}", contestEntryForm.errorsAsJson());
        }
        return new ReturnHelper(!contestEntryForm.hasErrors(), result).toResult();
    }

    private static List<String> validateContestEntry (Contest contest, List<ObjectId> objectIds) {
        List<String> errores = new ArrayList<>();

        // Verificar que el contest sea válido
        if (contest == null) {
            errores.add(ERROR_CONTEST_INVALID);
        }
        else {
            // Verificar que el contest esté activo (ni "live" ni "history")
            if (!contest.isActive()) {
                errores.add(ERROR_CONTEST_NOT_ACTIVE);
            }

            // Buscar los soccerPlayers dentro de los partidos del contest
            List<InstanceSoccerPlayer> soccerPlayers = getSoccerPlayersFromContest(objectIds, contest);

            // Verificar que TODOS los futbolistas seleccionados participen en los partidos del contest
            if (objectIds.size() != soccerPlayers.size()) {
                // No hemos podido encontrar todos los futbolistas referenciados por el contest entry
                errores.add(ERROR_FANTASY_TEAM_INCOMPLETE);
            }
            else {
                // Verificar que los futbolistas no cuestan más que el salaryCap del contest
                if (getSalaryCap(soccerPlayers) > contest.salaryCap) {
                    errores.add(ERROR_SALARYCAP_INVALID);
                }

                // Verificar que todos las posiciones del team están completas
                if (!isFormationValid(soccerPlayers)) {
                    errores.add(ERROR_FORMATION_INVALID);
                }

                // Verificar que no se han incluido muchos players del mismo equipo
                if (!isMaxPlayersFromSameTeamValid(contest, soccerPlayers)) {
                    errores.add(ERROR_MAX_PLAYERS_SAME_TEAM);
                }
            }
        }

        return errores;
    }

    private static List<InstanceSoccerPlayer> getSoccerPlayersFromContest(List<ObjectId> ids, Contest contest) {
        List<InstanceSoccerPlayer> soccerPlayers = new ArrayList<>();
        for (ObjectId soccerPlayerId : ids) {
            for (InstanceSoccerPlayer instancePlayer : contest.instanceSoccerPlayers) {
                if (soccerPlayerId.equals(instancePlayer.templateSoccerPlayerId)) {
                    soccerPlayers.add(instancePlayer);
                    break;
                }
            }
        }
        return soccerPlayers;
    }

    private static int getSalaryCap(List<InstanceSoccerPlayer> soccerPlayers) {
        int salaryCapTeam = 0;
        for (InstanceSoccerPlayer soccer : soccerPlayers) {
            salaryCapTeam += soccer.salary;
        }
        return salaryCapTeam;
    }

    private static boolean isFormationValid(List<InstanceSoccerPlayer> soccerPlayers) {
        return  (countFieldPos(FieldPos.GOALKEEPER, soccerPlayers) == 1) &&
                (countFieldPos(FieldPos.DEFENSE, soccerPlayers) == 4) &&
                (countFieldPos(FieldPos.MIDDLE, soccerPlayers) == 4) &&
                (countFieldPos(FieldPos.FORWARD, soccerPlayers) == 2);
    }

    private static boolean isMaxPlayersFromSameTeamValid(Contest contest, List<InstanceSoccerPlayer> soccerPlayers) {
        boolean valid = true;

        Map<String, Integer> numPlayersFromTeam = new HashMap<>();
        for (InstanceSoccerPlayer player : soccerPlayers) {
            String key = player.templateSoccerTeamId.toString();
            Integer num = numPlayersFromTeam.containsKey(key)
                    ? numPlayersFromTeam.get(key)
                    : 0;
            if (num < contest.getMaxPlayersFromSameTeam()) {
                numPlayersFromTeam.put(key, num + 1);
            }
            else {
                valid = false;
                break;
            }
        }

        return valid;
    }

    private static int countFieldPos(FieldPos fieldPos, List<InstanceSoccerPlayer> soccerPlayers) {
        int count = 0;
        for (InstanceSoccerPlayer soccerPlayer : soccerPlayers) {
            if (soccerPlayer.fieldPos.equals(fieldPos)) {
                count++;
            }
        }
        return count;
    }
}
