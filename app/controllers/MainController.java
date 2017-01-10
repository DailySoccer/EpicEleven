package controllers;

import actions.AllowCors;
import actions.UserAuthenticated;
import com.google.common.collect.ImmutableMap;
import model.*;
import model.opta.OptaCompetition;
import org.bson.types.ObjectId;
import play.Logger;
import play.cache.Cache;
import play.cache.Cached;
import play.data.Form;
import play.data.validation.Constraints;
import play.libs.F;
import play.libs.F.Promise;
import play.libs.ws.WS;
import play.mvc.Controller;
import play.mvc.Result;
import play.mvc.With;
import utils.ListUtils;
import utils.ReturnHelper;
import utils.SessionUtils;

import java.util.*;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;

import static akka.pattern.Patterns.ask;
import static play.data.Form.form;

@AllowCors.Origin
public class MainController extends Controller {

    private final static int ACTOR_TIMEOUT = 10000;
    private final static String MAINTENANCE = "maintenance";
    private final static int CACHE_LEADERBOARD = 12 * 60 * 60;              // 12 horas
    private final static int CACHE_SOCCERPLAYER_BY_COMPETITION = 15 * 60;   // 15 minutos
    private final static int CACHE_TEMPLATESOCCERPLAYERS = 8 * 60 * 60;     // 8 Horas
    private final static int CACHE_TEMPLATESOCCERTEAMS = 24 * 60 * 60;

    public static Result maintenance() {
        return badRequest(MAINTENANCE);
    }

    public static Result maintenance(String parameters) {
        return maintenance();
    }

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

    private static List<UserRanking> getUsersRanking() {
        List<UserRanking> usersRanking = new ArrayList<UserRanking>();

        User.findAll(JsonViews.Leaderboard.class).forEach(user -> usersRanking.add( new UserRanking(user) ) );

        return usersRanking;
    }

    public static Result getLeaderboard() throws Exception {
        User theUser = SessionUtils.getUserFromRequest(Controller.ctx().request());

        List<UserRanking> userRankingList = Cache.getOrElse("Leaderboard", new Callable<List<UserRanking>>() {
            @Override
            public List<UserRanking> call() throws Exception {
                return getUsersRanking();
            }
        }, CACHE_LEADERBOARD);

        // Asumimos una caché inválida, porque para que sea válida el usuario tendría que estar registrado en la misma
        boolean validCache = (theUser == null);

        if (!validCache) {
            // Comprobar si coinciden los datos de la caché con los del usuario que los solicita
            String userId = theUser.userId.toString();
            for (UserRanking userRanking : userRankingList) {
                if (userRanking.getUserId().equals(userId)) {
                    // Si lo que tenemos en la cache tiene los mismos datos que los que tiene actualmente el usuario
                    // consideraremos a la caché válida
                    validCache = userRanking.getEarnedMoney().equals(theUser.earnedMoney)
                            && (userRanking.getTrueSkill() == theUser.trueSkill);
                    break;
                }
            }
        }

        // Hay que reconstruir la caché?
        if (!validCache) {
            Logger.debug("getLeaderboard: cache INVALID");

            userRankingList = getUsersRanking();
            Cache.set("Leaderboard", userRankingList);
        }
        else {
            Logger.debug("getLeaderboard: cache OK");
        }

        return new ReturnHelper(ImmutableMap.of("users", userRankingList)).toResult(JsonViews.Leaderboard.class);
    }

    private static List<UserRanking> getSortedUsersRanking() {
        List<UserRanking> usersRanking = new ArrayList<UserRanking>();

        List<User> users = User.findAll(JsonViews.Leaderboard.class);

        // Creamos la estructura de ranking de usuarios
        users.forEach(user -> usersRanking.add( new UserRanking(user) ) );

        // Obtenemos la posición de ranking de cada uno de los usuarios
        class UserValue {
            public int index = 0;       // índice en la tabla general de "users"
            public float value = 0;      // valor a comparar (gold o trueskill)
            public UserValue(int index, float value) {
                this.index = index;
                this.value = value;
            }
        }

        // Crear 2 lista para obtener el ranking de trueskill y gold
        List<UserValue> skillRanking = new ArrayList<>(users.size());
        List<UserValue> goldRanking = new ArrayList<>(users.size());
        for (int i=0; i<users.size(); i++) {
            User user = users.get(i);
            skillRanking.add( new UserValue(i, user.trueSkill) );
            goldRanking.add( new UserValue(i, user.earnedMoney.getAmount().floatValue()) );
        }

        Collections.sort(skillRanking, (v1, v2) -> Float.compare(v2.value, v1.value));
        Collections.sort(goldRanking, (v1, v2) -> Float.compare(v2.value, v1.value));

        // Registrar el ranking en la lista de ranking de usuarios
        for (int i=0; i<users.size(); i++) {
            UserValue skillRank = skillRanking.get(i);
            usersRanking.get(skillRank.index).put("skillRank", i+1);

            UserValue goldRank = goldRanking.get(i);
            usersRanking.get(goldRank.index).put("goldRank", i+1);
        }

        return usersRanking;
    }

    public static Promise<Result> getLeaderboardV2() throws Exception {
        ObjectId userId = SessionUtils.getUserIdFromRequest(Controller.ctx().request());
        return QueryManager.getUserRankingList(userId != null ? userId.toString() : null)
                .map(response -> {
                    List<UserRanking> userRankingList = (List<UserRanking>) response;
                    return new ReturnHelper(ImmutableMap.of("users", userRankingList)).toResult(JsonViews.Leaderboard.class);
                });
    }

    public static Promise<Result> getLeaderboardV3() throws Exception {
        User theUser = SessionUtils.getUserFromRequest(Controller.ctx().request());

        return QueryManager.getUserRankingList(theUser != null ? theUser.userId.toString() : null)
                .map(response -> {
                    List<UserRanking> userRankingList = (List<UserRanking>) response;

                    if (theUser != null) {
                        // Limitar la información que enviamos
                        String userId = theUser.userId.toString();

                        // Buscar el usuario en el ranking
                        UserRanking userRanking = null;
                        for (UserRanking ranking : userRankingList) {
                            if (ranking.getUserId().equals(userId)) {
                                userRanking = ranking;
                                break;
                            }
                        }
                        if (userRanking != null) {
                            // Únicamente enviaremos el top de cada ranking y los usuarios que están "cerca" del usuario
                            int userSkillRank = userRanking.getSkillRank();
                            int userGoldRank = userRanking.getGoldRank();

                            List<UserRanking> filteredRankingList = userRankingList.stream().filter( ranking -> {
                                int skillRank = ranking.getSkillRank();
                                int goldRank = ranking.getGoldRank();
                                return ( skillRank < 10 || goldRank <= 10 || Math.abs(userSkillRank - skillRank) < 10 || Math.abs(userGoldRank - goldRank) < 10);
                            }).collect(Collectors.toList());

                            return new ReturnHelper(ImmutableMap.of("users", filteredRankingList)).toResult(JsonViews.Leaderboard.class);
                        }
                        else {
                            Logger.error("getLeaderboardV3: User invalid: {}", theUser.userId.toString());
                        }
                    }

                    return new ReturnHelper(ImmutableMap.of("users", userRankingList)).toResult(JsonViews.Leaderboard.class);
                });
    }

    /*
     * Obtener la información sobre un InstanceSoccerPlayer (estadísticas,...)
     */
    public static Promise<Result> getInstanceSoccerPlayerInfo(String contestId, String templateSoccerPlayerId) {
        return QueryManager.getTemplateSoccerPlayerInfo(templateSoccerPlayerId)
                .map(response -> {
                    InstanceSoccerPlayer instanceSoccerPlayer = InstanceSoccerPlayer.findOne(new ObjectId(contestId), new ObjectId(templateSoccerPlayerId));

                    Map<String, Object> data = (Map<String, Object>) response;
                    data.put("instance_soccer_player", instanceSoccerPlayer);
                    return new ReturnHelper(data).toResult(JsonViews.Statistics.class);
                });
    }

    /*
     * Obtener la información sobre un SoccerPlayer (estadísticas,...)
     */
    public static Promise<Result> getTemplateSoccerPlayerInfo(String templateSoccerPlayerId) {
        return QueryManager.getTemplateSoccerPlayerInfo(templateSoccerPlayerId)
                .map(response -> {
                    Map<String, Object> data = (Map<String, Object>) response;
                    data.put("instance_soccer_player", new InstanceSoccerPlayer((TemplateSoccerPlayer) data.get("soccer_player")));
                    return new ReturnHelper(data).toResult(JsonViews.Statistics.class);
                });
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

    public static Promise<Result> getTemplateSoccerPlayersV2() {
        return QueryManager.getTemplateSoccerPlayersV2()
                .map(response -> (Result) response);
    }

    public static Promise<Result> getTemplateSoccerTeams() {
        return QueryManager.getTemplateSoccerTeams()
                .map(response -> (Result) response);
    }

    public static Promise<Result> getSoccerPlayersByCompetition(String competitionId) throws Exception {
        return QueryManager.getSoccerPlayersByCompetition(competitionId)
                .map(response -> (Result) response);
    }

    public static Promise<Result> getSoccerPlayersByCompetition_23() {
        return QueryManager.getSoccerPlayersByCompetition("23")
                .map(response -> (Result) response);
    }

    public static Promise<Result> getSoccerPlayersByCompetition_8() {
        return QueryManager.getSoccerPlayersByCompetition("8")
                .map(response -> (Result) response);
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
    public static Result claimReward() {
        User theUser = (User) ctx().args.get("User");

        theUser.claimReward();

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
