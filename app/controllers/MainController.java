package controllers;

import actions.AllowCors;
import com.google.common.collect.ImmutableMap;
import model.*;
import org.bson.types.ObjectId;
import play.Logger;
import play.mvc.Controller;
import play.mvc.Result;
import utils.ListUtils;
import utils.ReturnHelper;

import java.util.*;

@AllowCors.Origin
public class MainController extends Controller {

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

        List<TemplateSoccerTeam> templateSoccerTeams = !templateSoccerTeamIds.isEmpty() ? TemplateSoccerTeam.findAll(ListUtils.asList(templateSoccerTeamIds.iterator()))
                : new ArrayList<TemplateSoccerTeam>();

        Map<String, Object> data = new HashMap<>();
        data.put("soccer_teams", templateSoccerTeams);
        data.put("soccer_player", templateSoccerPlayer);
        if (templateMatchEvent != null) {
            data.put("match_event", templateMatchEvent);
        }
        return new ReturnHelper(data).toResult(JsonViews.Extended.class);
    }
}
