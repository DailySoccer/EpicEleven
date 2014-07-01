package controllers.admin;

import model.Model;
import model.TemplateSoccerPlayer;
import model.TemplateSoccerTeam;
import org.bson.types.ObjectId;
import play.mvc.Controller;
import play.mvc.Result;
import utils.ListUtils;

import java.util.List;

public class TemplateSoccerTeamController extends Controller {
    public static Result index() {
        Iterable<TemplateSoccerTeam> soccerTeamResults = Model.templateSoccerTeams().find().as(TemplateSoccerTeam.class);
        List<TemplateSoccerTeam> soccerTeamList = ListUtils.asList(soccerTeamResults);

        return ok(views.html.template_soccer_team_list.render(soccerTeamList));
    }

    public static Result show(String templateSoccerTeamId) {
        TemplateSoccerTeam templateSoccerTeam = Model.templateSoccerTeams().findOne("{ _id : # }",
                new ObjectId(templateSoccerTeamId)).as(TemplateSoccerTeam.class);
        Iterable<TemplateSoccerPlayer> soccerPlayerResults = Model.templateSoccerPlayers().find("{ templateTeamId: # }",
                new ObjectId(templateSoccerTeamId)).as(TemplateSoccerPlayer.class);
        List<TemplateSoccerPlayer> soccerPlayerList = ListUtils.asList(soccerPlayerResults);
        return ok(views.html.template_soccer_team.render(templateSoccerTeam, soccerPlayerList));
    }
}
