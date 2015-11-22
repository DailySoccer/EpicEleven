package controllers.admin;

import com.google.common.collect.ImmutableList;
import org.bson.types.ObjectId;
import model.*;
import play.Logger;
import play.mvc.*;
import java.util.*;

import model.opta.OptaEvent;
import utils.ListUtils;

public class TemplateMatchEventController extends Controller {
    public static Result index() {
        return ok(views.html.template_match_event_list.render(TemplateMatchEvent.findAll(), TemplateSoccerTeam.findAllAsMap()));
    }

    public static Result indexAjax() {
        HashMap<ObjectId, TemplateSoccerTeam> teamsMap = TemplateSoccerTeam.findAllAsMap();

        return PaginationData.withAjax(request().queryString(), Model.templateMatchEvents(), TemplateMatchEvent.class, new PaginationData() {
            public List<String> getFieldNames() {
                return ImmutableList.of(
                        "optaMatchEventId",
                        "optaTeamAId",
                        "optaTeamBId",
                        "templateMatchEventId",
                        "optaCompetitionId",
                        "startDate",
                        "",                         // State
                        ""                          // Simulation
                );
            }

            public String getFieldByIndex(Object data, Integer index) {
                TemplateMatchEvent templateMatchEvent = (TemplateMatchEvent) data;
                switch (index) {
                    case 0:
                        return templateMatchEvent.optaMatchEventId;
                    case 1:
                        return templateMatchEvent.optaTeamAId;
                    case 2:
                        return templateMatchEvent.optaTeamBId;
                    case 3:
                        return (teamsMap.containsKey(templateMatchEvent.templateSoccerTeamAId) ? teamsMap.get(templateMatchEvent.templateSoccerTeamAId).name : templateMatchEvent.templateSoccerTeamAId.toString())
                                + " vs " +
                                (teamsMap.containsKey(templateMatchEvent.templateSoccerTeamBId) ? teamsMap.get(templateMatchEvent.templateSoccerTeamBId).name : templateMatchEvent.templateSoccerTeamBId.toString());
                    case 4:
                        return templateMatchEvent.optaCompetitionId;
                    case 5:
                        return GlobalDate.formatDate(templateMatchEvent.startDate);
                    case 6: if(templateMatchEvent.isGameFinished()) {
                                return "Finished";
                            } else if(templateMatchEvent.isGameStarted()) {
                                return "Live";
                            } else {
                                return "Waiting";
                            }
                    case 7:
                        return templateMatchEvent.simulation ? "Simulation" : "Real";
                }
                return "<invalid value>";
            }

            public String getRenderFieldByIndex(Object data, String fieldValue, Integer index) {
                TemplateMatchEvent templateMatchEvent = (TemplateMatchEvent) data;
                switch (index) {
                    case 3:
                        String result = fieldValue;
                        if (templateMatchEvent.isGameStarted()) {
                            String teamAName = (teamsMap.containsKey(templateMatchEvent.templateSoccerTeamAId) ? teamsMap.get(templateMatchEvent.templateSoccerTeamAId).name : templateMatchEvent.templateSoccerTeamAId.toString());
                            String teamBName = (teamsMap.containsKey(templateMatchEvent.templateSoccerTeamBId) ? teamsMap.get(templateMatchEvent.templateSoccerTeamBId).name : templateMatchEvent.templateSoccerTeamBId.toString());
                            result = String.format("%s (%d) vs %s (%d)",
                                    teamAName, templateMatchEvent.getFantasyPointsForTeam(templateMatchEvent.templateSoccerTeamAId), teamBName, templateMatchEvent.getFantasyPointsForTeam(templateMatchEvent.templateSoccerTeamBId));
                            if (templateMatchEvent.homeScore != -1 && templateMatchEvent.awayScore != -1) {
                                result = String.format("%s (%d - %d)", result, templateMatchEvent.homeScore, templateMatchEvent.awayScore);
                            }
                        }
                        return String.format("<a href=\"%s\">%s</a>", routes.TemplateMatchEventController.show(templateMatchEvent.templateMatchEventId.toString()), result);
                    case 6:
                        if(fieldValue.equals("Finished")) {
                            return "<button class=\"btn btn-danger\">Finished</button>";
                        } else if(fieldValue.equals("Live")) {
                            return String.format("<button class=\"btn btn-success\">Live - %d min.</button>", templateMatchEvent.minutesPlayed);
                        } else {
                            return "<button class=\"btn btn-warning\">Waiting</button>";
                        }
                    case 7:
                        return templateMatchEvent.simulation
                                ? String.format("<button class=\"btn btn-success\">Simulation</button>")
                                : "";
                }
                return fieldValue;
            }
        });
    }

    public static Result show(String templateMatchEventId) {
        return ok(views.html.template_match_event.render(TemplateMatchEvent.findOne(new ObjectId(templateMatchEventId)), TemplateSoccerTeam.findAllAsMap()));
    }

    public static Result showOptaEvents(String matchEventId) {
        TemplateMatchEvent matchEvent = TemplateMatchEvent.findOne(new ObjectId(matchEventId));

        Iterable<OptaEvent> optaEventResults = Model.optaEvents().find("{gameId: #, points: { $ne: 0 }}", matchEvent.optaMatchEventId).as(OptaEvent.class);
        List<OptaEvent> optaEventList = ListUtils.asList(optaEventResults);

        return ok(views.html.match_event_opta_events_list.render(optaEventList, getOptaPlayersInfo(matchEvent)));
    }

    public static Result showSimulatedEvents(String matchEventId) {
        TemplateMatchEvent matchEvent = TemplateMatchEvent.findOne(new ObjectId(matchEventId));
        return ok(views.html.match_event_simulated_events_list.render(matchEvent.simulationEvents, getTemplateSoccerPlayersInfo(matchEvent)));
    }

    public static Result showManagerLevels(String matchEventId) {
        TemplateMatchEvent matchEvent = TemplateMatchEvent.findOne(new ObjectId(matchEventId));
        printAsTablePlayerManagerLevel(matchEvent);
        return ok(views.html.template_match_event.render(matchEvent, TemplateSoccerTeam.findAllAsMap()));
    }

    public static Result simulate(String templateMatchEventId, Integer num) {

        TemplateMatchEvent matchEvent = TemplateMatchEvent.findOne(new ObjectId(templateMatchEventId));
        TemplateSoccerTeam teamHome = TemplateSoccerTeam.findOne(matchEvent.templateSoccerTeamAId);
        TemplateSoccerTeam teamAway = TemplateSoccerTeam.findOne(matchEvent.templateSoccerTeamBId);
        for (int i=0; i<num; i++) {
            TemplateMatchEvent clone = matchEvent.copy();

            MatchEventSimulation simulation = new MatchEventSimulation(matchEvent.templateMatchEventId);
            clone.applySimulationEventsAtLiveFantasyPoints(simulation.simulationEvents);

            Logger.info("{}: {}({}) {} vs {} {}({})", i,
                    teamHome.name, clone.getFantasyPointsForTeam(teamHome.templateSoccerTeamId), clone.homeScore,
                    clone.awayScore, teamAway.name, clone.getFantasyPointsForTeam(teamAway.templateSoccerTeamId));
        }

        return redirect(routes.TemplateMatchEventController.show(templateMatchEventId));
    }

    private static void printAsTablePlayerManagerLevel(TemplateMatchEvent templateMatchEvent) {
        StringBuffer buffer = new StringBuffer();

        TemplateSoccerTeam teamA = TemplateSoccerTeam.findOne(templateMatchEvent.templateSoccerTeamAId);
        TemplateSoccerTeam teamB = TemplateSoccerTeam.findOne(templateMatchEvent.templateSoccerTeamBId);

        buffer.append("<h3>" + teamA.name + " vs " + teamB.name + "</h3>");
        buffer.append("<table border=\"1\" style=\"width:40%; text-align: center; \">\n" +
                "        <tr>\n" +
                "        <td><strong>Manager Level<strong></td>\n" +
                "        <td><strong>GoalKeepers<strong></td>\n" +
                "        <td><strong>Defense<strong></td>\n" +
                "        <td><strong>Middle<strong></td>\n" +
                "        <td><strong>Forward<strong></td>\n" +
                "        </tr>");
        for (int i=0; i<User.MANAGER_POINTS.length; i++) {
            List<TemplateSoccerPlayer> availables = TemplateSoccerPlayer.soccerPlayersAvailables(templateMatchEvent, i);
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

    private static HashMap<String, String> getTemplateSoccerPlayersInfo(TemplateMatchEvent matchEvent){
        HashMap<String, String> map = new HashMap<>();

        TemplateSoccerTeam templateSoccerTeamA = TemplateSoccerTeam.findOne(matchEvent.templateSoccerTeamAId);
        for (TemplateSoccerPlayer soccerPlayer : TemplateSoccerPlayer.findAllFromTemplateTeam(templateSoccerTeamA.templateSoccerTeamId)) {
            map.put(soccerPlayer.templateSoccerPlayerId.toString(), soccerPlayer.name);
            map.put(soccerPlayer.templateSoccerPlayerId.toString().concat("-team"), templateSoccerTeamA.name);
        }

        TemplateSoccerTeam templateSoccerTeamB = TemplateSoccerTeam.findOne(matchEvent.templateSoccerTeamBId);
        for (TemplateSoccerPlayer soccerPlayer : TemplateSoccerPlayer.findAllFromTemplateTeam(templateSoccerTeamB.templateSoccerTeamId)) {
            map.put(soccerPlayer.templateSoccerPlayerId.toString(), soccerPlayer.name);
            map.put(soccerPlayer.templateSoccerPlayerId.toString() + "-team", templateSoccerTeamB.name);
        }
        return map;
    }

    private static HashMap<String, String> getOptaPlayersInfo(TemplateMatchEvent matchEvent){
        HashMap<String, String> map = new HashMap<>();

        TemplateSoccerTeam templateSoccerTeamA = TemplateSoccerTeam.findOne(matchEvent.templateSoccerTeamAId);
        for (TemplateSoccerPlayer soccerPlayer : TemplateSoccerPlayer.findAllFromTemplateTeam(templateSoccerTeamA.templateSoccerTeamId)) {
            map.put(soccerPlayer.optaPlayerId, soccerPlayer.name);
            map.put(soccerPlayer.optaPlayerId.concat("-team"), templateSoccerTeamA.name);
        }

        TemplateSoccerTeam templateSoccerTeamB = TemplateSoccerTeam.findOne(matchEvent.templateSoccerTeamBId);
        for (TemplateSoccerPlayer soccerPlayer : TemplateSoccerPlayer.findAllFromTemplateTeam(templateSoccerTeamB.templateSoccerTeamId)) {
            map.put(soccerPlayer.optaPlayerId, soccerPlayer.name);
            map.put(soccerPlayer.optaPlayerId + "-team", templateSoccerTeamB.name);
        }
        return map;
    }

}
