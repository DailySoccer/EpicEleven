package controllers;

import actions.AllowCors;
import actions.UserAuthenticated;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableList;
import model.*;
import model.jobs.CancelContestEntryJob;
import model.jobs.EnterContestJob;
import org.bson.types.ObjectId;
import org.joda.money.Money;
import play.Logger;
import play.Play;
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
    private static final String ERROR_MANAGER_LEVEL_INVALID = "ERROR_MANAGER_LEVEL_INVALID";
    private static final String ERROR_TRUESKILL_INVALID = "ERROR_TRUESKILL_INVALID";
    private static final String ERROR_OP_UNAUTHORIZED = "ERROR_OP_UNAUTHORIZED";
    private static final String ERROR_USER_ALREADY_INCLUDED = "ERROR_USER_ALREADY_INCLUDED";
    private static final String ERROR_USER_BALANCE_NEGATIVE = "ERROR_USER_BALANCE_NEGATIVE";
    private static final String ERROR_MAX_PLAYERS_SAME_TEAM = "ERROR_MAX_PLAYERS_SAME_TEAM";
    private static final String ERROR_RETRY_OP = "ERROR_RETRY_OP";

    public static class AddContestEntryParams {
        @Constraints.Required
        public String formation;

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
        String formation;

        if (!contestEntryForm.hasErrors()) {
            AddContestEntryParams params = contestEntryForm.get();

            contestIdRequested = params.contestId;
            formation = params.formation;

            // Buscar el contest : ObjectId
            Contest aContest = Contest.findOne(contestIdRequested);

            // Obtener los soccerIds de los futbolistas : List<ObjectId>
            List<ObjectId> idsList = ListUtils.objectIdListFromJson(params.soccerTeam);

            List<String> errores = new ArrayList<>();
            if (aContest != null) {
                if (aContest.containsContestEntryWithUser(theUser.userId)) {
                    errores.add(ERROR_USER_ALREADY_INCLUDED);
                }
                // Verificar que el contest no esté lleno (<= 0 : Ilimitado número de participantes)
                else if (aContest.maxEntries >= 0 && aContest.contestEntries.size() >= aContest.maxEntries) {
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
                errores = validateContestEntry(aContest, formation, idsList);
            }

            if (errores.isEmpty()) {
                if (MoneyUtils.isGreaterThan(aContest.entryFee, MoneyUtils.zero)) {
                    Money moneyNeeded = aContest.entryFee;

                    // En los torneos Oficiales, el usuario también tiene que pagar a los futbolistas
                    if (aContest.entryFee.getCurrencyUnit().equals(MoneyUtils.CURRENCY_GOLD)) {
                        Money managerBalance = User.calculateManagerBalance(theUser.userId);

                        List<InstanceSoccerPlayer> soccerPlayers = aContest.getInstanceSoccerPlayers(idsList);
                        moneyNeeded = moneyNeeded.plus(User.moneyToBuy(managerBalance, soccerPlayers));
                        Logger.debug("addContestEntry: moneyNeeded: {}", moneyNeeded.toString());
                    }

                    // Verificar que el usuario tiene dinero suficiente...
                    if (!User.hasMoney(theUser.userId, moneyNeeded)) {
                        errores.add(ERROR_USER_BALANCE_NEGATIVE);
                    }
                }
            }

            if (errores.isEmpty() && aContest.hasManagerLevelConditions()) {
                Money managerBalance = User.calculateManagerBalance(theUser.userId);
                int managerLevel = (int) User.managerLevelFromPoints(managerBalance);
                if (!aContest.managerLevelValid(managerLevel)) {
                    errores.add(ERROR_MANAGER_LEVEL_INVALID);
                }
            }

            if (errores.isEmpty() && aContest.hasTrueSkillConditions()) {
                if (!aContest.trueSkillValid(theUser.trueSkill)) {
                    errores.add(ERROR_TRUESKILL_INVALID);
                }
            }

            if (errores.isEmpty()) {
                if (aContest == null) {
                    throw new RuntimeException("WTF 8639: aContest != null");
                }

                // Los contests creados por los usuarios se activan cuando el author entra un contestEntry
                if (aContest.state.isWaitingAuthor()) {
                    Model.contests().update("{_id: #, state: \"WAITING_AUTHOR\"}", aContest.contestId).with("{$set: {state: \"ACTIVE\"}}");

                    if (aContest.simulation) {
                        aContest.setupSimulation();
                    }

                    // Durante el desarrollo permitimos que los mockUsers puedan entrar en un contest
                    if (Play.isDev()) {
                        boolean mockDataUsers = aContest.name.contains(TemplateContest.FILL_WITH_MOCK_USERS);
                        if (mockDataUsers) {
                            MockData.addContestEntries(aContest, (aContest.maxEntries > 0) ? aContest.maxEntries - 1 : 50);
                        }
                    }
                }

                EnterContestJob enterContestJob = EnterContestJob.create(theUser.userId, contestIdValid, formation, idsList);

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

            // Enviamos un perfil de usuario actualizado, dado que habrá gastado energía o gold al entrar en el constest
            result = ImmutableMap.of(
                    "result", "ok",
                    "contestId", contestIdValid.toString(),
                    "profile", User.findOne(theUser.userId).getProfile());
        }
        else {
            Logger.warn("addContestEntry failed: userId: {}: contestId: {}: error: {}", theUser.userId.toString(), contestIdRequested, contestEntryForm.errorsAsJson());
        }
        return new ReturnHelper(!contestEntryForm.hasErrors(), result).toResult();
    }

    public static class EditContestEntryParams {
        @Constraints.Required
        public String formation;

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

            String formation = params.formation;

            Logger.info("editContestEntry: contestEntryId({}) soccerTeam({})", params.contestEntryId, params.soccerTeam);

            User theUser = (User) ctx().args.get("User");

            ContestEntry contestEntry = ContestEntry.findOne(params.contestEntryId);
            if (contestEntry != null) {
                // Obtener el contestId : ObjectId
                Contest aContest = Contest.findOneFromContestEntry(contestEntry.contestEntryId);

                // Obtener los soccerIds de los futbolistas : List<ObjectId>
                List<ObjectId> idsList = ListUtils.objectIdListFromJson(params.soccerTeam);

                List<String> errores = validateContestEntry(aContest, formation, idsList);

                if (errores.isEmpty()) {
                    if (MoneyUtils.isGreaterThan(aContest.entryFee, MoneyUtils.zero) &&
                        aContest.entryFee.getCurrencyUnit().equals(MoneyUtils.CURRENCY_GOLD)) {
                        Money moneyNeeded = Money.zero(MoneyUtils.CURRENCY_GOLD);

                        // Averiguar cuánto dinero ha usado para comprar futbolistas de nivel superior
                        Money managerBalance = User.calculateManagerBalance(theUser.userId);
                        List<InstanceSoccerPlayer> soccerPlayers = aContest.getInstanceSoccerPlayers(idsList);
                        List<InstanceSoccerPlayer> playersToBuy = contestEntry.playersNotPurchased(User.playersToBuy(managerBalance, soccerPlayers));
                        if (!playersToBuy.isEmpty()) {
                            moneyNeeded = moneyNeeded.plus(User.moneyToBuy(managerBalance, playersToBuy));
                            Logger.debug("editContestEntry: moneyNeeded: {}", moneyNeeded.toString());

                            // Verificar que el usuario tiene dinero suficiente...
                            if (!User.hasMoney(theUser.userId, moneyNeeded)) {
                                errores.add(ERROR_USER_BALANCE_NEGATIVE);
                            }
                        }
                    }
                }

                if (errores.isEmpty()) {
                    if (!ContestEntry.update(theUser, aContest, contestEntry, formation, idsList)) {
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
                if (!contest.state.isActive()) {
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

    private static List<String> validateContestEntry (Contest contest, String formation, List<ObjectId> objectIds) {
        List<String> errores = new ArrayList<>();

        // Verificar que el contest sea válido
        if (contest == null) {
            errores.add(ERROR_CONTEST_INVALID);
        }
        else {
            // Verificar que el contest esté activo (ni "live" ni "history")
            if (!contest.state.isActive() && !contest.state.isWaitingAuthor()) {
                errores.add(ERROR_CONTEST_NOT_ACTIVE);
            }

            // Buscar los soccerPlayers dentro de los partidos del contest
            List<InstanceSoccerPlayer> soccerPlayers = contest.getInstanceSoccerPlayers(objectIds);

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
                if (!isFormationValid(formation, soccerPlayers)) {
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

    private static int getSalaryCap(List<InstanceSoccerPlayer> soccerPlayers) {
        int salaryCapTeam = 0;
        for (InstanceSoccerPlayer soccer : soccerPlayers) {
            salaryCapTeam += soccer.salary;
        }
        return salaryCapTeam;
    }

    private static boolean isFormationValid(String formation, List<InstanceSoccerPlayer> soccerPlayers) {
        boolean result = ContestEntry.FORMATIONS.stream().anyMatch( value -> value.equals(formation) );
        if (result) {
            int defenses = Character.getNumericValue(formation.charAt(0));
            int middles = Character.getNumericValue(formation.charAt(1));
            int forwards = Character.getNumericValue(formation.charAt(2));
            // Logger.debug("defenses: {} middles: {} forward: {}", defenses, middles, forwards);

            result = (countFieldPos(FieldPos.GOALKEEPER, soccerPlayers) == 1) &&
                    (countFieldPos(FieldPos.DEFENSE, soccerPlayers) == defenses) &&
                    (countFieldPos(FieldPos.MIDDLE, soccerPlayers) == middles) &&
                    (countFieldPos(FieldPos.FORWARD, soccerPlayers) == forwards);
        }
        return result;
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
