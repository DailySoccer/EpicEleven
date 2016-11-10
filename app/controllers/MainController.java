package controllers;

import actions.AllowCors;
import actions.UserAuthenticated;
import com.google.common.collect.ImmutableMap;
import model.*;
import model.opta.OptaCompetition;
import model.opta.OptaTeam;
import org.bson.types.ObjectId;
import play.Logger;
import play.cache.Cache;
import play.cache.Cached;
import play.data.Form;
import play.data.validation.Constraints;
import play.libs.F;
import play.libs.ws.WS;
import play.mvc.Controller;
import play.mvc.Result;
import play.mvc.With;
import utils.ListUtils;
import utils.ReturnHelper;

import java.util.*;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;

import static play.data.Form.form;

@AllowCors.Origin
public class MainController extends Controller {

    private final static int CACHE_SOCCERPLAYER_BY_COMPETITION = 15 * 60;   // 15 minutos
    private final static int CACHE_TEMPLATESOCCERPLAYERS = 8 * 60 * 60;     // 8 Horas
    private final static int CACHE_TEMPLATESOCCERTEAMS = 24 * 60 * 60;

    public static Result ping() {
    	return ok("Pong");
    }

    // Toda nuestra API va con preflight. No hemos tenido mas remedio, porque se nos planteaba el siguiente problema:
    // Para evitar el preflight, solo puedes mandar las "simple-headers'. Nuestro sessionToken entonces lo mandamos
    // en el POST dentro del www-form-urlencoded, ok, pero en el GET dentro de la queryString => malo para la seguridad.
    //
    // Podriamos hacer que toda el API fuera entonces por POST y meter el sessionToken UrlEnconded, pero no nos parece
    // elegante. Hasta que se demuestre lo contrario, usamos el preflight con max-age agresivo.
    //
    public static Result preFlight(String path) {
        AllowCors.preFlight(request(), response());
        return ok();
    }

    /*
     * Fecha actual del servidor: Puede ser la hora en tiempo real o la fecha de simulacion
     */
    public static Result getCurrentDate() {
        return new ReturnHelper(ImmutableMap.of("currentDate", GlobalDate.getCurrentDate())).toResult();
    }


    public static Result getScoringRules() {
        return new ReturnHelper(ImmutableMap.of("scoring_rules", PointsTranslation.getAllCurrent())).toResult();
    }

    public static Result getLeaderboard() {
        return new ReturnHelper(ImmutableMap.of("users", UserInfo.findAllWithAchievements())).toResult();
    }

    /*
     * Obtener la información sobre un InstanceSoccerPlayer (estadísticas,...)
     */
    public static Result getInstanceSoccerPlayerInfo(String contestId, String templateSoccerPlayerId) {
        InstanceSoccerPlayer instanceSoccerPlayer = InstanceSoccerPlayer.findOne(new ObjectId(contestId), new ObjectId(templateSoccerPlayerId));
        TemplateSoccerPlayer templateSoccerPlayer = TemplateSoccerPlayer.findOne(new ObjectId(templateSoccerPlayerId));

        Set<ObjectId> templateSoccerTeamIds = new HashSet<>();

        // Añadimos el equipo en el que juega actualmente el futbolista
        templateSoccerTeamIds.add(templateSoccerPlayer.templateTeamId);

        // Añadimos los equipos CONTRA los que ha jugado el futbolista
        for (SoccerPlayerStats stats : templateSoccerPlayer.stats) {
            templateSoccerTeamIds.add(stats.opponentTeamId);
        }

        // Incluimos el próximo partido que jugará el futbolista (y sus equipos)
        TemplateMatchEvent templateMatchEvent = TemplateMatchEvent.findNextMatchEvent(templateSoccerPlayer.templateTeamId);
        if (templateMatchEvent != null) {
            templateSoccerTeamIds.add(templateMatchEvent.templateSoccerTeamAId);
            templateSoccerTeamIds.add(templateMatchEvent.templateSoccerTeamBId);
        }

        List<TemplateSoccerTeam> templateSoccerTeams = !templateSoccerTeamIds.isEmpty() ? TemplateSoccerTeam.findAll(ListUtils.asList(templateSoccerTeamIds))
                : new ArrayList<TemplateSoccerTeam>();

        Map<String, Object> data = new HashMap<>();
        data.put("soccer_teams", templateSoccerTeams);
        data.put("soccer_player", templateSoccerPlayer);
        data.put("instance_soccer_player", instanceSoccerPlayer);
        if (templateMatchEvent != null) {
            data.put("match_event", templateMatchEvent);
        }
        return new ReturnHelper(data).toResult(JsonViews.Statistics.class);
    }

    /*
     * Obtener la información sobre un SoccerPlayer (estadísticas,...)
     */
    public static Result getTemplateSoccerPlayerInfo(String templateSoccerPlayerId) {

        TemplateSoccerPlayer templateSoccerPlayer = TemplateSoccerPlayer.findOne(new ObjectId(templateSoccerPlayerId));

        Set<ObjectId> templateSoccerTeamIds = new HashSet<>();

        // Añadimos el equipo en el que juega actualmente el futbolista
        templateSoccerTeamIds.add(templateSoccerPlayer.templateTeamId);

        // Añadimos los equipos CONTRA los que ha jugado el futbolista
        for (SoccerPlayerStats stats : templateSoccerPlayer.stats) {
            templateSoccerTeamIds.add(stats.opponentTeamId);
        }

        // Incluimos el próximo partido que jugará el futbolista (y sus equipos)
        TemplateMatchEvent templateMatchEvent = TemplateMatchEvent.findNextMatchEvent(templateSoccerPlayer.templateTeamId);
        if (templateMatchEvent != null) {
            templateSoccerTeamIds.add(templateMatchEvent.templateSoccerTeamAId);
            templateSoccerTeamIds.add(templateMatchEvent.templateSoccerTeamBId);
        }

        List<TemplateSoccerTeam> templateSoccerTeams = !templateSoccerTeamIds.isEmpty() ? TemplateSoccerTeam.findAll(ListUtils.asList(templateSoccerTeamIds))
                : new ArrayList<TemplateSoccerTeam>();

        Map<String, Object> data = new HashMap<>();
        data.put("soccer_teams", templateSoccerTeams);
        data.put("soccer_player", templateSoccerPlayer);
        data.put("instance_soccer_player", new InstanceSoccerPlayer(templateSoccerPlayer));
        if (templateMatchEvent != null) {
            data.put("match_event", templateMatchEvent);
        }
        return new ReturnHelper(data).toResult(JsonViews.Statistics.class);
    }

    @With(AllowCors.CorsAction.class)
    @Cached(key = "TemplateSoccerPlayers", duration = CACHE_TEMPLATESOCCERPLAYERS)
    public static Result getTemplateSoccerPlayers() {
        List<TemplateSoccerPlayer> templateSoccerPlayers = TemplateSoccerPlayer.findAll();

        for (TemplateSoccerPlayer player : templateSoccerPlayers) {
            player.stats = player.stats.stream().filter(
                    stat -> stat.hasPlayed() &&
                            stat.startDate.after(OptaCompetition.SEASON_DATE_START) &&
                            !stat.optaCompetitionId.equals(OptaCompetition.CHAMPIONS_LEAGUE)
            ).collect(Collectors.toList());
        }

        ImmutableMap.Builder<Object, Object> builder = ImmutableMap.builder()
                .put("template_soccer_players", templateSoccerPlayers);
        return new ReturnHelper(builder.build())
                .toResult(JsonViews.Extended.class);
    }

    @With(AllowCors.CorsAction.class)
    @Cached(key = "TemplateSoccerPlayersV2", duration = CACHE_TEMPLATESOCCERPLAYERS)
    public static Result getTemplateSoccerPlayersV2() {
        List<TemplateSoccerPlayer> templateSoccerPlayers = TemplateSoccerPlayer.findAll();

        ImmutableMap.Builder<Object, Object> builder = ImmutableMap.builder()
                .put("template_soccer_players", templateSoccerPlayers);
        return new ReturnHelper(builder.build())
                .toResult(JsonViews.Template.class);
    }

    @With(AllowCors.CorsAction.class)
    @Cached(key = "TemplateSoccerTeams", duration = CACHE_TEMPLATESOCCERTEAMS)
    public static Result getTemplateSoccerTeams() {
        ImmutableMap.Builder<Object, Object> builder = ImmutableMap.builder()
                .put("template_soccer_teams", TemplateSoccerTeam.findAll());
        return new ReturnHelper(builder.build())
                .toResult(JsonViews.Template.class);
    }

    // @UserAuthenticated
    public static Result getSoccerPlayersByCompetition(String competitionId) throws Exception {
        return Cache.getOrElse("SoccerPlayersByCompetition-".concat(competitionId), new Callable<Result>() {
            @Override
            public Result call() throws Exception {
                return getSoccerPlayersByCompetition_internal(competitionId);
            }
        }, CACHE_SOCCERPLAYER_BY_COMPETITION);
    }

    @With(AllowCors.CorsAction.class)
    @Cached(key = "SoccerPlayersByCompetition-23", duration = CACHE_SOCCERPLAYER_BY_COMPETITION)
    public static Result getSoccerPlayersByCompetition_23() {
        return getSoccerPlayersByCompetition_internal("23");
    }

    @With(AllowCors.CorsAction.class)
    @Cached(key = "SoccerPlayersByCompetition-8", duration = CACHE_SOCCERPLAYER_BY_COMPETITION)
    public static Result getSoccerPlayersByCompetition_8() {
        return getSoccerPlayersByCompetition_internal("8");
    }

    private static Result getSoccerPlayersByCompetition_internal (String competitionId) {
        List<TemplateSoccerTeam> templateSoccerTeamList = TemplateSoccerTeam.findAllByCompetition(competitionId);

        List<TemplateSoccerPlayer> templateSoccerPlayers = new ArrayList<>();
        for (TemplateSoccerTeam templateSoccerTeam : templateSoccerTeamList) {
            templateSoccerPlayers.addAll(templateSoccerTeam.getTemplateSoccerPlayersWithSalary());
        }

        List<InstanceSoccerPlayer> instanceSoccerPlayers = new ArrayList<>();
        for (TemplateSoccerPlayer templateSoccerPlayer : templateSoccerPlayers) {
            instanceSoccerPlayers.add( new InstanceSoccerPlayer(templateSoccerPlayer) );
        }

        ImmutableMap.Builder<Object, Object> builder = ImmutableMap.builder()
                .put("instanceSoccerPlayers", instanceSoccerPlayers)
                .put("soccer_teams", templateSoccerTeamList);

                /*
                if (theUser != null) {
                    builder.put("profile", theUser.getProfile());
                }
                */

        return new ReturnHelper(builder.build())
                .toResult(JsonViews.FullContest.class);
    }

    public static class FavoritesParams {
        @Constraints.Required
        public String soccerPlayers;   // JSON con la lista de futbolistas seleccionados
    }

    @UserAuthenticated
    public static Result setFavorites() {
        Form<FavoritesParams> form = form(FavoritesParams.class).bindFromRequest();

        User theUser = (User) ctx().args.get("User");

        if (!form.hasErrors()) {
            FavoritesParams params = form.get();

            // Obtener los soccerIds de los futbolistas : List<ObjectId>
            List<ObjectId> idsList = ListUtils.objectIdListFromJson(params.soccerPlayers);
            if (idsList != null) {
                theUser.setFavorites(idsList);
            }
        }

        return new ReturnHelper(!form.hasErrors(), ImmutableMap.builder()
                .put("profile", theUser.getProfile())
                .build())
                .toResult(JsonViews.FullContest.class);
    }

    @UserAuthenticated
    public static Result addFlag(String flag) {
        User theUser = (User) ctx().args.get("User");
        theUser.addFlag(flag);

        return new ReturnHelper(true, ImmutableMap.of(
                "result", "ok")).toResult();
    }

    @UserAuthenticated
    public static Result removeFlag(String flag) {
        User theUser = (User) ctx().args.get("User");
        theUser.removeFlag(flag);

        return new ReturnHelper(true, ImmutableMap.of(
                "result", "ok")).toResult();
    }

    @UserAuthenticated
    public static Result hasFlag(String flag) {
        User theUser = (User) ctx().args.get("User");
        return new ReturnHelper(true, ImmutableMap.of(
                "result", theUser.hasFlag(flag))).toResult();
    }

    @UserAuthenticated
    public static Result claimReward(String rewardId) {
        User theUser = (User) ctx().args.get("User");

        if (ObjectId.isValid(rewardId)) {
            theUser.claimReward(new ObjectId(rewardId));
        }

        return new ReturnHelper(true, ImmutableMap.of(
                "profile", theUser.getProfile())).toResult();
    }

    public static F.Promise<Result> getShortUrl() {
        String url = request().body().asJson().get("url").asText();
        return WS.url("http://www.readability.com/api/shortener/v1/urls").post("url="+url).map(response -> {
            return ok(response.getBody());
        });
    }

    public static Result termsOfUse() {
        return redirect("http://www.futbolcuatro.com/terminos-de-uso/");
    }

    public static Result privacyPolicy() {
        return redirect("http://www.futbolcuatro.com/politica-de-privacidad/");
    }
}
