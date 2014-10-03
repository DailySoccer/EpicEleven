package controllers;

import actions.AllowCors;
import com.google.common.collect.ImmutableMap;
import model.*;
import org.bson.types.ObjectId;
import play.mvc.Controller;
import play.mvc.Result;
import utils.ReturnHelper;

import java.util.ArrayList;
import java.util.List;

@AllowCors.Origin
public class MainController extends Controller {

    public static Result ping() {
    	return ok("Pong");
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

        List<ObjectId> templateSoccerTeamIds = new ArrayList<>();

        // Añadimos el equipo en el que juega actualmente el futbolista
        templateSoccerTeamIds.add(templateSoccerPlayer.templateTeamId);

        // Añadimos los equipos CONTRA los que ha jugado el futbolista
        for (SoccerPlayerStats stats : templateSoccerPlayer.stats) {
            templateSoccerTeamIds.add(stats.opponentTeamId);
        }

        List<TemplateSoccerTeam> templateSoccerTeams = !templateSoccerTeamIds.isEmpty() ? TemplateSoccerTeam.findAll(templateSoccerTeamIds)
                : new ArrayList<TemplateSoccerTeam>();

        return new ReturnHelper(ImmutableMap.of(
                "soccerTeams", templateSoccerTeams,
                "soccerPlayer", templateSoccerPlayer)
        ).toResult(JsonViews.Extended.class);
    }
}
