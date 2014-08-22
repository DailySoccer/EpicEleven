package controllers.admin;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.collect.ImmutableList;
import model.GlobalDate;
import model.Model;
import model.opta.*;
import org.jongo.Find;
import play.Logger;
import play.mvc.Controller;
import play.mvc.Result;
import play.libs.Json;
import utils.ListUtils;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.Map;
import java.util.List;

public class OptaController extends Controller {
    public static Result optaSoccerPlayers() {
        Iterable<OptaPlayer> optaPlayerResults = Model.optaPlayers().find().as(OptaPlayer.class);
        List<OptaPlayer> optaPlayerList = ListUtils.asList(optaPlayerResults);

        return ok(views.html.opta_soccer_player_list.render(optaPlayerList));
    }

    public static Result optaSoccerTeams() {
        Iterable<OptaTeam> optaTeamResults = Model.optaTeams().find().as(OptaTeam.class);
        List<OptaTeam> optaTeamList = ListUtils.asList(optaTeamResults);

        return ok(views.html.opta_soccer_team_list.render(optaTeamList));
    }

    public static Result optaMatchEvents() {
        Iterable<OptaMatchEvent> optaMatchEventResults = Model.optaMatchEvents().find().as(OptaMatchEvent.class);
        List<OptaMatchEvent> optaMatchEventList = ListUtils.asList(optaMatchEventResults);

        return ok(views.html.opta_match_event_list.render(optaMatchEventList));
    }

    public static Result optaEvents() {
        List<OptaEvent> optaEvents = ListUtils.asList(Model.optaEvents().find().as(OptaEvent.class));
        optaEvents.clear();

        return ok(views.html.opta_event_list.render(optaEvents));
    }

    static List<String> getOptaEventFieldNames() {
        return ImmutableList.of(
                "pointsTranslationId",
                "competitionId",
                "gameId",
                "homeTeamId",
                "awayTeamId",
                "optaPlayerId",
                "typeId",
                "points",
                "timestamp",
                "lastModified"
        );
    }

    static String getOptaEventFieldByIndex(OptaEvent optaEvent, Integer index) {
        switch (index) {
            case 0: return optaEvent.pointsTranslationId != null ? optaEvent.pointsTranslationId.toString() : "-";
            case 1: return optaEvent.competitionId;
            case 2: return optaEvent.gameId;
            case 3: return optaEvent.homeTeamId;
            case 4: return optaEvent.awayTeamId;
            case 5: return optaEvent.optaPlayerId;
            case 6: return String.valueOf(optaEvent.typeId);
            case 7: return String.valueOf(optaEvent.points);
            case 8: return GlobalDate.formatDate(optaEvent.timestamp);
            case 9: return GlobalDate.formatDate(optaEvent.lastModified);
        }
        return "<invalid value>";
    }

    public static Result optaEventsAjax() {
        Map<String, String[]> params = request().queryString();
        // Logger.info("params: {}", params);

        long iTotalRecords = Model.optaEvents().count();
        long iTotalDisplayRecords = iTotalRecords;
        String filter = params.get("sSearch")[0];
        Integer pageSize = Integer.valueOf(params.get("iDisplayLength")[0]);
        Integer page = Integer.valueOf(params.get("iDisplayStart")[0]) / pageSize;

        String sortBy = "_id";
        String order = params.get("sSortDir_0")[0];

        List<String> fieldNames = getOptaEventFieldNames();

        Integer iSort = Integer.valueOf(params.get("iSortCol_0")[0]);
        sortBy = fieldNames.get(iSort);

        Find find;

        List<OptaEvent> optaEvents;

        if (filter.isEmpty()) {
            find = Model.optaEvents().find()
                        .sort(String.format("{%s : %d}", sortBy, order.equals("asc") ? 1 : -1))
                        .skip(page * pageSize)
                        .limit(pageSize);

            optaEvents = ListUtils.asList(find.as(OptaEvent.class));
        }
        else {
            long startTime = System.currentTimeMillis();

            optaEvents = new ArrayList<>();

            find = Model.optaEvents().find()
                        .sort(String.format("{%s : %d}", sortBy, order.equals("asc") ? 1 : -1));

            Iterable<OptaEvent> results = find.as(OptaEvent.class);
            iTotalDisplayRecords = 0;

            Iterator<OptaEvent> it = results.iterator();
            while (it.hasNext()) {
                OptaEvent optaEvent = it.next();

                boolean valid = false;
                for (int i=0; i<fieldNames.size() && !valid; i++) {
                    String fieldValue = getOptaEventFieldByIndex(optaEvent, i);
                    valid = (fieldValue != null) && fieldValue.contains(filter);
                }
                if (valid) {
                    if ((iTotalDisplayRecords > (page * pageSize)) && (optaEvents.size() < pageSize)) {
                        optaEvents.add(optaEvent);
                    }
                    iTotalDisplayRecords++;
                }
            }

            Logger.info("elapsed: {}", System.currentTimeMillis() - startTime);

            /*
            StringBuffer strFilter = null;
            for (String fieldName : fieldNames) {
                if (strFilter == null) {
                    strFilter = new StringBuffer();
                    strFilter.append("{ $or: [");
                }
                else {
                    strFilter.append(", ");
                }
                strFilter.append(String.format("{%s: { $regex: \"%s\" }}", fieldName, filter));
            }
            strFilter.append("] }");

            find = Model.optaEvents().find(strFilter.toString());

            iTotalDisplayRecords = Model.optaEvents().count(strFilter.toString());
            */
        }

        ObjectNode result = Json.newObject();

        result.put("sEcho", Integer.valueOf(params.get("sEcho")[0]));
        result.put("iTotalRecords", iTotalRecords);
        result.put("iTotalDisplayRecords", iTotalDisplayRecords);

        ArrayNode an = result.putArray("aaData");

        for(OptaEvent optaEvent : optaEvents) {
            ObjectNode row = Json.newObject();
            for (int i=0; i<fieldNames.size(); i++) {
                row.put(String.valueOf(i), getOptaEventFieldByIndex(optaEvent, i));
            }
            an.add(row);
        }

        return ok(result);
    }

    public static Result updateOptaEvents() {

        new OptaProcessor().recalculateAllEvents();

        FlashMessage.success("All OptaEvents recalculated with the current points translation table");
        return redirect(routes.PointsTranslationController.index());
    }


}
