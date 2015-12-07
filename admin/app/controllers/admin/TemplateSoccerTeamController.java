package controllers.admin;

import model.*;
import model.opta.OptaEvent;
import model.opta.OptaEventType;
import model.opta.OptaMatchEventStats;
import org.bson.types.ObjectId;
import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormat;
import play.mvc.Controller;
import play.mvc.Result;
import utils.FileUtils;

import java.util.ArrayList;
import java.util.HashMap;
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

    public static Result statisticsToCSV(String templateSoccerTeamId) {

        TemplateSoccerTeam templateSoccerTeam = TemplateSoccerTeam.findOne(new ObjectId(templateSoccerTeamId));

        List<OptaEvent> optaEventList = OptaEvent.filterByOptaTeam(templateSoccerTeam.optaTeamId);

        List<String> headers = new ArrayList<String>() {{
            add("GameId");
            add("Type");
            add("TypeId");
            add("Points");
            add("OptaPlayerId");
            add("PlayerMinutes");
            add("CompetitionId");
            add("EventId");
            add("HomeTeamId");
            add("HomeTeamELO");
            add("AwayTeamId");
            add("AwayTeamELO");
            add("TimeStamp");
            add("Minutes");
        }};

        Map<String, OptaMatchEventStats> matchEventStatsMap = new HashMap<>();
        List<String> body = new ArrayList<>();

        optaEventList.forEach( optaEvent -> {
            body.add(optaEvent.gameId);
            body.add(OptaEventType.getEnum(optaEvent.typeId).name());
            body.add(String.valueOf(optaEvent.typeId));
            body.add(String.valueOf(optaEvent.points));
            body.add(optaEvent.optaPlayerId);

            OptaMatchEventStats matchEventStats = null;
            int playedMinutes = 0;

            if (matchEventStatsMap.containsKey(optaEvent.gameId)) {
                matchEventStats = matchEventStatsMap.get(optaEvent.gameId);
            }
            else {
                matchEventStats = OptaMatchEventStats.findOne(optaEvent.gameId);
                matchEventStatsMap.put(optaEvent.gameId, matchEventStats);
            }
            playedMinutes = (matchEventStats != null) ? matchEventStats.getPlayedMinutes(optaEvent.optaPlayerId) : 0;
            body.add(String.valueOf(playedMinutes));

            body.add(optaEvent.competitionId);
            body.add(String.valueOf(optaEvent.eventId));
            body.add(optaEvent.homeTeamId);
            body.add(String.valueOf(TemplateSoccerTeam.getELO(optaEvent.homeTeamId)));
            body.add(optaEvent.awayTeamId);
            body.add(String.valueOf(TemplateSoccerTeam.getELO(optaEvent.awayTeamId)));
            //body.add(GlobalDate.formatDate(optaEvent.timestamp));
            body.add(new DateTime(optaEvent.timestamp).toString(DateTimeFormat.forPattern("yyyy/MM/dd").withZoneUTC()));
            body.add(String.valueOf(optaEvent.min));
        });

        String fileName = String.format("%s.csv", templateSoccerTeam.name);
        FileUtils.generateCsv(fileName, headers, body);

        FlashMessage.info(fileName);

        return redirect(routes.TemplateSoccerTeamController.show(templateSoccerTeamId));
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
