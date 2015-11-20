package controllers.admin;

import model.*;
import org.bson.types.ObjectId;
import play.mvc.Controller;
import play.mvc.Result;

import java.util.List;
import java.util.Map;

public class TemplateSoccerTeamController extends Controller {
    public static Result index() {
        return ok(views.html.template_soccer_team_list.render(TemplateSoccerTeam.findAll()));
    }

    public static Result show(String templateSoccerTeamId) {
        TemplateSoccerTeam templateSoccerTeam = TemplateSoccerTeam.findOne(new ObjectId(templateSoccerTeamId));
        return ok(views.html.template_soccer_team.render(
                templateSoccerTeam,
                templateSoccerTeam.getTemplateSoccerPlayers()));
    }

    public static Result showManagerLevels(String templateSoccerTeamId) {
        TemplateSoccerTeam templateSoccerTeam = TemplateSoccerTeam.findOne(new ObjectId(templateSoccerTeamId));

        printAsTablePlayerManagerLevel(templateSoccerTeam);

        return ok(views.html.template_soccer_team.render(
                templateSoccerTeam,
                templateSoccerTeam.getTemplateSoccerPlayers()));
    }

    private static void printAsTablePlayerManagerLevel(TemplateSoccerTeam templateSoccerTeam) {
        StringBuffer buffer = new StringBuffer();

        buffer.append("<h3>" + templateSoccerTeam.name + "</h3>");
        buffer.append("<table border=\"1\" style=\"width:40%; text-align: center; \">\n" +
                "        <tr>\n" +
                "        <td><strong>Manager Level<strong></td>\n" +
                "        <td><strong>GoalKeepers<strong></td>\n" +
                "        <td><strong>Defense<strong></td>\n" +
                "        <td><strong>Middle<strong></td>\n" +
                "        <td><strong>Forward<strong></td>\n" +
                "        </tr>");
        for (int i=0; i< User.MANAGER_POINTS.length; i++) {
            List<TemplateSoccerPlayer> availables = TemplateSoccerPlayer.soccerPlayersAvailables(templateSoccerTeam, i);
            Map<String, Long> frequency = TemplateSoccerPlayer.frequencyFieldPos(availables);

            buffer.append("<tr>");
            buffer.append("<td>" + i + "</td>");
            buffer.append("<td>" + frequency.get(FieldPos.GOALKEEPER.name()) + "</td>");
            buffer.append("<td>" + frequency.get(FieldPos.DEFENSE.name()) +"</td>");
            buffer.append("<td>" + frequency.get(FieldPos.MIDDLE.name()) +"</td>");
            buffer.append("<td>" + frequency.get(FieldPos.FORWARD.name()) +"</td>");

            buffer.append("</tr>");
        }
        buffer.append("</table>");

        FlashMessage.info(buffer.toString());
    }
}
