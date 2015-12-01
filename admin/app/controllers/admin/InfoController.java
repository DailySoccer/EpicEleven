package controllers.admin;

import model.*;
import model.opta.OptaCompetition;
import model.opta.OptaTeam;
import org.bson.types.ObjectId;
import play.Logger;
import play.data.Form;
import play.mvc.Controller;
import play.mvc.Result;
import utils.ReturnHelper;

import java.util.*;

public class InfoController extends Controller {
    public static Result index() {
        return ok(views.html.info.render());
    }

    static Form<QueryPlayersForm> queryPlayersForm = Form.form(QueryPlayersForm.class);
    static HashMap<String, Integer> queryPlayersResult = new HashMap<String, Integer>();

    public static Result queryPlayers() {
        List<TemplateSoccerTeam> templateSoccerTeams = getTeamsInCompetition("ES_PL");
        HashMap<String, String> teamOptions = getTeamOptions(templateSoccerTeams);

        return ok(views.html.info_players.render(queryPlayersForm, teamOptions, queryPlayersResult));
    }

    public static Result queryPlayersSubmit() {
        List<TemplateSoccerTeam> templateSoccerTeams = getTeamsInCompetition("ES_PL");
        HashMap<String, String> teamOptions = getTeamOptions(templateSoccerTeams);

        queryPlayersForm = Form.form(QueryPlayersForm.class).bindFromRequest();
        if (queryPlayersForm.hasErrors()) {
            return badRequest(views.html.info_players.render(queryPlayersForm, teamOptions, queryPlayersResult));
        }

        QueryPlayersForm params = queryPlayersForm.get();

        Logger.debug("Teams: {}", params.templateSoccerTeams.size());
        for (String templateSoccerTeam : params.templateSoccerTeams) {
            Logger.debug("Selected: {}", templateSoccerTeam);
        }

        return ok(views.html.info_players.render(queryPlayersForm, teamOptions, queryPlayersResult));
    }

    private static List<TemplateSoccerTeam> getTeamsInCompetition (String competitionCode) {
        List<TemplateSoccerTeam> templateSoccerTeams = new ArrayList<>();
        List<OptaTeam> optaTeams = OptaCompetition.findTeamsByCompetitionCode(competitionCode);
        for (OptaTeam optaTeam : optaTeams) {
            templateSoccerTeams.add(TemplateSoccerTeam.findOneFromOptaId(optaTeam.optaTeamId));
        }
        return templateSoccerTeams;
    }

    public static HashMap<String, String> getTeamOptions(List<TemplateSoccerTeam> templateSoccerTeams) {
        HashMap<String, String> options = new LinkedHashMap<>();

        for (TemplateSoccerTeam templateSoccerTeam: templateSoccerTeams) {
            options.put(templateSoccerTeam.templateSoccerTeamId.toString(), templateSoccerTeam.name);
        }

        return options;
    }
}
