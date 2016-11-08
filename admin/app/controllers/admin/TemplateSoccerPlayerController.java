package controllers.admin;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import model.*;
import model.opta.*;
import org.bson.types.ObjectId;
import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormat;
import play.Logger;
import play.mvc.Controller;
import play.mvc.Result;
import utils.FileUtils;

import java.util.*;

public class TemplateSoccerPlayerController extends Controller {
    public static Result index() {
        return ok(views.html.template_soccer_player_list.render(false));
    }

    public static Result editSalaries() {
        return ok(views.html.template_soccer_player_list.render(true));
    }

    public static Result indexAjax() {
        return PaginationData.withAjax(request().queryString(), Model.templateSoccerPlayers(), TemplateSoccerPlayer.class, new PaginationData() {
            public List<String> getFieldNames() {
                return ImmutableList.of(
                        "tags",
                        "optaPlayerId",
                        "name",
                        "fieldPos",
                        "salary",
                        "fantasyPoints",
                        "",
                        "",                 // Team
                        "createdAt"
                );
            }

            public String getFieldByIndex(Object data, Integer index) {
                TemplateSoccerPlayer templateSoccerPlayer = (TemplateSoccerPlayer) data;
                switch (index) {
                    case 0: return templateSoccerPlayer.tags==null?"": Joiner.on(", ").join(templateSoccerPlayer.tags);
                    case 1: return templateSoccerPlayer.optaPlayerId;
                    case 2: return templateSoccerPlayer.name;
                    case 3: return templateSoccerPlayer.fieldPos.toString();
                    case 4: return String.valueOf(templateSoccerPlayer.salary);
                    case 5: return String.valueOf(templateSoccerPlayer.fantasyPoints);
                    case 6: return String.valueOf(templateSoccerPlayer.getPlayedMatches());
                    case 7:
                            TemplateSoccerTeam templateSoccerTeam = TemplateSoccerTeam.findOne(templateSoccerPlayer.templateTeamId);
                            return templateSoccerTeam.name;
                    case 8: return String.valueOf(templateSoccerPlayer.createdAt);
                }
                return "<invalid value>";
            }

            public String getRenderFieldByIndex(Object data, String fieldValue, Integer index) {
                TemplateSoccerPlayer templateSoccerPlayer = (TemplateSoccerPlayer) data;
                switch (index) {
                    case 4:
                        return String.format("<p class=\"edit-salary\" contenteditable=\"false\" data-player-id=%s>%s</p>",
                                    templateSoccerPlayer.templateSoccerPlayerId,
                                    fieldValue);
                    case 5:
                        return String.format("<a href=\"%s\" tabIndex=\"-1\">%s</a>",
                                    routes.TemplateSoccerPlayerController.showStats(templateSoccerPlayer.templateSoccerPlayerId.toString()),
                                    fieldValue);
                    case 7:
                        return String.format("<a href=\"%s\" tabIndex=\"-1\">%s</a>",
                                    routes.TemplateSoccerTeamController.show(templateSoccerPlayer.templateTeamId.toString()),
                                    fieldValue);
                }
                return fieldValue;
            }
        });
    }

    public static Result showFantasyPointsInContest(String contestId, String playerId) {
        List<OptaEvent> optaEventList = new ArrayList<>();

        TemplateSoccerPlayer templateSoccerPlayer = TemplateSoccerPlayer.findOne(new ObjectId(playerId));
        Contest contest = Contest.findOne(new ObjectId(contestId));
        TemplateContest templateContest = TemplateContest.findOne(contest.templateContestId);
        List<TemplateMatchEvent> templateMatchEvents = templateContest.getTemplateMatchEvents();

        for (TemplateMatchEvent templateMatchEvent : templateMatchEvents) {
            optaEventList.addAll(OptaEvent.filter(templateMatchEvent.optaMatchEventId, templateSoccerPlayer.optaPlayerId));
        }

        return ok(views.html.player_fantasy_points.render(templateSoccerPlayer, optaEventList));
    }

    public static Result showFantasyPointsInMatchEvent(String templateMatchEventId, String playerId) {
        List<OptaEvent> optaEventList = new ArrayList<>();

        TemplateSoccerPlayer templateSoccerPlayer = TemplateSoccerPlayer.findOne(new ObjectId(playerId));
        TemplateMatchEvent templateMatchEvent = TemplateMatchEvent.findOne(new ObjectId(templateMatchEventId));

        optaEventList.addAll(OptaEvent.filter(templateMatchEvent.optaMatchEventId, templateSoccerPlayer.optaPlayerId));

        return ok(views.html.player_fantasy_points.render(templateSoccerPlayer, optaEventList));
    }

    public static Result showPlayerLiveEventsInMatchEvent(String templateMatchEventId, String playerId) {
        HashMap<String, LiveEventInfo> events = new HashMap<>();

        TemplateSoccerPlayer templateSoccerPlayer = TemplateSoccerPlayer.findOne(new ObjectId(playerId));
        TemplateMatchEvent templateMatchEvent = TemplateMatchEvent.findOne(new ObjectId(templateMatchEventId));

        LiveFantasyPoints liveFantasyPoints = templateMatchEvent.getLiveFantasyPointsBySoccerPlayer(templateSoccerPlayer.templateSoccerPlayerId);
        if (liveFantasyPoints != null) {
            events.putAll(liveFantasyPoints.events);
        }

        return ok(views.html.player_live_events.render(templateSoccerPlayer, events));
    }

    public static Result showStats(String playerId) {
        TemplateSoccerPlayer templateSoccerPlayer = TemplateSoccerPlayer.findOne(new ObjectId(playerId));
        return ok(views.html.template_soccer_player_stats.render(templateSoccerPlayer));
    }

    public static Result statisticsToCSV(String templateSoccerPlayerId) {

        TemplateSoccerPlayer templateSoccerPlayer = TemplateSoccerPlayer.findOne(new ObjectId(templateSoccerPlayerId));

        List<OptaEvent> optaEventList = OptaEvent.filterByOptaPlayer(templateSoccerPlayer.optaPlayerId);

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
        });

        String fileName = String.format("%s.csv", templateSoccerPlayer.name);
        FileUtils.generateCsv(fileName, headers, body);

        FlashMessage.info(fileName);

        return redirect(routes.TemplateSoccerPlayerController.showStats(templateSoccerPlayerId));
    }

    public static Result matchStatisticsToCSV(String templateSoccerPlayerId) {

        TemplateSoccerPlayer templateSoccerPlayer = TemplateSoccerPlayer.findOne(new ObjectId(templateSoccerPlayerId));

        List<OptaEvent> optaEventList = OptaEvent.filterByOptaPlayer(templateSoccerPlayer.optaPlayerId);

        List<String> headers = new ArrayList<String>() {{
            add("GameId");
            add("FantasyPoints");
            add("PlayedMinutes");
        }};

        List<String> body = new ArrayList<>();

        templateSoccerPlayer.stats.forEach(stats -> {
            body.add(stats.optaMatchEventId);
            body.add(String.valueOf(stats.fantasyPoints));
            body.add(String.valueOf(stats.playedMinutes));
        });

        String fileName = String.format("%s resumen.csv", templateSoccerPlayer.name);
        FileUtils.generateCsv(String.format(fileName, templateSoccerPlayer.name), headers, body);

        FlashMessage.info(fileName);

        return redirect(routes.TemplateSoccerPlayerController.showStats(templateSoccerPlayerId));
    }

    public static Result showChangesList() {
        Map<String, Map<String, String>> changesList = getChangesListFromOpta();
        return ok(views.html.template_soccer_player_changes.render(changesList));
    }

    public static Result importFromOpta() {
        Map<String, Map<String, String>> changesList = getChangesListFromOpta();

        HashSet<String> optaPlayerIds = new HashSet<>();
        for (String optaId : changesList.keySet()) {
            optaPlayerIds.add(optaId);
            Logger.debug("change: optaId: {}", optaId);
        }
        Logger.debug("Total Changes: {} documents", optaPlayerIds.size());

        new OptaImporter(null, optaPlayerIds, null).process();

        FlashMessage.info("Import ƒrom Opta " + optaPlayerIds.size() + " documents");

        return redirect(routes.TemplateSoccerPlayerController.showChangesList());
    }

    private static Map<String, Map<String, String>> getChangesListFromOpta() {
        Map<String, Map<String, String>> results = new HashMap<>();

        HashMap<ObjectId, TemplateSoccerTeam> templateSoccerTeamMap = TemplateSoccerTeam.findAllAsMap();
        HashMap<String, OptaTeam> optaTeamMap = OptaTeam.findAllAsMap();

        HashMap<String, ObjectId> optaTeamToTemplate = new HashMap<>();
        templateSoccerTeamMap.forEach((k, v) -> optaTeamToTemplate.put(v.optaTeamId, k));

        List<TemplateSoccerPlayer> templateSoccerPlayers = TemplateSoccerPlayer.findAllWithProjection(JsonViews.CheckChanges.class);

        HashMap<String, TemplateSoccerPlayer> optaIdToTemplate = new HashMap<>();
        for (TemplateSoccerPlayer templateSoccerPlayer : templateSoccerPlayers) {
            optaIdToTemplate.put(templateSoccerPlayer.optaPlayerId, templateSoccerPlayer);
        }

        // Recorremos todos los optaPlayers para encontrar aquellos templateSoccerPlayers que no tengamos correctamente actualizados
        for (OptaPlayer optaPlayer : OptaPlayer.findAll()) {
            TemplateSoccerPlayer templateSoccerPlayer = optaIdToTemplate.containsKey(optaPlayer.optaPlayerId) ? optaIdToTemplate.get(optaPlayer.optaPlayerId) : null;

            // Es un futbolista NUEVO?
            if (templateSoccerPlayer == null) {
                // Equipo inválido?
                if (!optaTeamMap.containsKey(optaPlayer.teamId)) {
                    /*
                    final Map<String, String> data = ImmutableMap.<String, String>builder()
                            .put("Name", optaPlayer.name)
                            .put("TeamOld", "-")
                            .put("TeamNew", optaPlayer.teamId)
                            .put("FieldPos", optaPlayer.position)
                            .put("Action", "INVALID")
                            .build();
                    results.put(optaPlayer.optaPlayerId, data);
                    */
                    Logger.error("INVALID: Name ({}) TeamOld({}) TeamNew({}), FieldPos({})",
                            optaPlayer.name, "-", optaPlayer.teamId, optaPlayer.position);
                }
                else {
                    final Map<String, String> data = ImmutableMap.<String, String>builder()
                            .put("Name", optaPlayer.name)
                            .put("TeamOld", templateSoccerTeamMap.get(templateSoccerPlayer.templateTeamId).name)
                            .put("TeamNew", optaTeamMap.get(optaPlayer.teamId).name)
                            .put("FieldPos", optaPlayer.position)
                            // .put("UpdatedTime", GlobalDate.formatDate(optaPlayer.updatedTime))
                            .put("Action", "NEW")
                            .build();
                    results.put(optaPlayer.optaPlayerId, data);
                    Logger.debug("NEW: Name ({}) TeamOld({}) TeamNew({}), FieldPos({})",
                            optaPlayer.name, templateSoccerTeamMap.get(templateSoccerPlayer.templateTeamId).name, optaTeamMap.get(optaPlayer.teamId).name, optaPlayer.position);
                }
            }
            // Tiene algún CAMBIO?
            else if (templateSoccerPlayer.hasChanged(optaPlayer, optaTeamToTemplate.get(optaPlayer.teamId))) {
                final Map<String, String> data = ImmutableMap.<String, String>builder()
                        .put("Name", optaPlayer.name)
                        .put("TeamOld", templateSoccerTeamMap.get(templateSoccerPlayer.templateTeamId).name)
                        .put("TeamNew", optaTeamMap.get(optaPlayer.teamId).name)
                        .put("FieldPos", optaPlayer.position)
                        // .put("UpdatedTime", GlobalDate.formatDate(optaPlayer.updatedTime))
                        .put("Action", "CHANGE")
                        .build();
                results.put(optaPlayer.optaPlayerId, data);
                Logger.debug("CHANGE: Name ({}) TeamOld({}) TeamNew({}), FieldPos({})",
                        optaPlayer.name, templateSoccerTeamMap.get(templateSoccerPlayer.templateTeamId).name, optaTeamMap.get(optaPlayer.teamId).name, optaPlayer.position);
            }
        }

        return results;
    }
}
