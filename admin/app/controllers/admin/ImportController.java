package controllers.admin;



import model.*;
import model.opta.*;
import play.mvc.Controller;
import play.mvc.Result;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;

public class ImportController extends Controller {
    /**
     * IMPORT TEAMS from OPTA
     *
     */
    public static Result showImportTeams() {
        List<String> competitionsActivated = OptaCompetition.asIds(OptaCompetition.findAllActive());

        return ok(views.html.import_teams.render(evaluateDirtyTeams(competitionsActivated), "*", OptaCompetition.asMap(OptaCompetition.findAllActive())));
    }

    public static Result showImportTeamsFromCompetition(String competitionId) {
        List<String> competitionsSelected = new ArrayList<>();
        competitionsSelected.add(competitionId);

        return ok(views.html.import_teams.render(evaluateDirtyTeams(competitionsSelected), competitionId, OptaCompetition.asMap(OptaCompetition.findAllActive())));
    }

    /**
     * IMPORT SOCCERS from OPTA
     *
     */
    public static Result showImportSoccers() {
        return ok(views.html.import_soccers.render(evaluateDirtySoccers()));
    }

    /**
     * IMPORT MATCH EVENTS from OPTA
     *
     */
    public static Result showImportMatchEvents() {
        return ok(views.html.import_match_events.render(evaluateDirtyMatchEvents(), OptaTeam.findAllAsMap()));
    }

    private static List<OptaTeam> evaluateDirtyTeams(List<String> seasonCompetitionIds) {
        List<OptaTeam> invalidates  = new ArrayList<>();

        Iterable<OptaTeam> teamsDirty = Model.optaTeams().find("{dirty: true, seasonCompetitionIds: {$in: #}}", seasonCompetitionIds).as(OptaTeam.class);

        for(OptaTeam optaTeam : teamsDirty) {
            TemplateSoccerTeam template = TemplateSoccerTeam.findOneFromOptaId(optaTeam.optaTeamId);

            if (template == null) {
                if (TemplateSoccerTeam.isInvalidFromImport(optaTeam)) {
                    invalidates.add(optaTeam);
                }

            }
        }
        return invalidates;
    }

    private static List<OptaPlayer> evaluateDirtySoccers() {
        List<OptaPlayer> invalidates = new ArrayList<>();
        HashMap<String, Boolean> teamIsValid = new HashMap<>();
        Iterable<OptaPlayer> soccersDirty = Model.optaPlayers().find("{dirty: true}").as(OptaPlayer.class);

        for(OptaPlayer optaSoccer : soccersDirty) {
            Boolean isTeamValid = false;
            if (teamIsValid.containsKey(optaSoccer.teamId)) {
                isTeamValid = teamIsValid.get(optaSoccer.teamId);
            }
            else {
                OptaTeam optaTeam = OptaTeam.findOne(optaSoccer.teamId);
                if (optaTeam != null) {
                    for (int i = 0; i < optaTeam.seasonCompetitionIds.size() && !isTeamValid; i++) {
                        isTeamValid = OptaCompetition.findOne(optaTeam.seasonCompetitionIds.get(i)).activated;
                    }
                }
                teamIsValid.put(optaSoccer.teamId, isTeamValid);
            }

            TemplateSoccerPlayer template = TemplateSoccerPlayer.findOneFromOptaId(optaSoccer.optaPlayerId);
            if (template == null) {
                // No queremos añadir futbolistas de equipos inválidos
                if (isTeamValid) {
                    if (TemplateSoccerPlayer.isInvalidFromImport(optaSoccer)) {
                        invalidates.add(optaSoccer);
                    }
                }
            }

        }
        return invalidates;
    }

    private static List<OptaMatchEvent> evaluateDirtyMatchEvents() {
        List<OptaMatchEvent> invalidates = new ArrayList<>();
        Date now = GlobalDate.getCurrentDate();
        Iterable<OptaMatchEvent> matchesDirty = Model.optaMatchEvents().find("{dirty: true, matchDate: {$gte: #}}", now).as(OptaMatchEvent.class);

        for (OptaMatchEvent optaMatch : matchesDirty) {
            TemplateMatchEvent template = TemplateMatchEvent.findOneFromOptaId(optaMatch.optaMatchEventId);
            if (template == null) {
                if (TemplateMatchEvent.isInvalidFromImport(optaMatch)) {
                    if (invalidates != null)
                        invalidates.add(optaMatch);
                }
            }
        }
        return invalidates;
    }
}
