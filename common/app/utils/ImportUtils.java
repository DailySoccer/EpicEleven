package utils;

import model.*;
import model.opta.OptaCompetition;
import model.opta.OptaMatchEvent;
import model.opta.OptaPlayer;
import model.opta.OptaTeam;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;

public class ImportUtils {
    public static void importAll() {
        importTeams();
        importPlayers();
        importMatchEvents();
    }

    public static int importTeams(List<OptaTeam> optaTeams) {
        int imported = 0;
        for(OptaTeam optaTeam : optaTeams) {
            if (TemplateSoccerTeam.importTeam(optaTeam)) {
                imported++;
            }
        }
        return imported;
    }

    public static int importPlayers(List<OptaPlayer> optaPlayers) {
        int imported = 0;
        for(OptaPlayer optaPlayer : optaPlayers) {
            if (TemplateSoccerPlayer.importSoccer(optaPlayer)) {
                imported++;
            }
        }
        return imported;
    }

    public static int importMatchEvents(List<OptaMatchEvent> optaMatchEvents) {
        int imported = 0;
        for(OptaMatchEvent optaMatchEvent : optaMatchEvents) {
            if (TemplateMatchEvent.importMatchEvent(optaMatchEvent)) {
                imported++;
            }
        }
        return imported;
    }

    private static void importTeams() {
        List<String> competitionsActivated = OptaCompetition.asIds(OptaCompetition.findAllActive());
        List<OptaTeam> teamsNew = new ArrayList<>();
        List<OptaTeam> teamsChanged = new ArrayList<>();
        evaluateDirtyTeams(competitionsActivated, teamsNew, teamsChanged, null);

        importTeams(teamsNew);
        importTeams(teamsChanged);
    }

    private static void importPlayers() {
        List<OptaPlayer> playersNew = new ArrayList<>();
        List<OptaPlayer> playersChanged = new ArrayList<>();
        evaluateDirtySoccers(playersNew, playersChanged, null);

        importPlayers(playersNew);
        importPlayers(playersChanged);
    }

    private static void importMatchEvents() {
        List<OptaMatchEvent> matchesNew = new ArrayList<>();
        List<OptaMatchEvent> matchesChanged = new ArrayList<>();
        evaluateDirtyMatchEvents(matchesNew, matchesChanged, null);

        importMatchEvents(matchesNew);
        importMatchEvents(matchesChanged);
    }

    public static void evaluateDirtyTeams(List<String> seasonCompetitionIds, List<OptaTeam> news, List<OptaTeam> changes, List<OptaTeam> invalidates) {
        Iterable<OptaTeam> teamsDirty = Model.optaTeams().find("{dirty: true, seasonCompetitionIds: {$in: #}}", seasonCompetitionIds).as(OptaTeam.class);
        for(OptaTeam optaTeam : teamsDirty) {
            TemplateSoccerTeam template = TemplateSoccerTeam.findOneFromOptaId(optaTeam.optaTeamId);
            if (template == null) {
                if (TemplateSoccerTeam.isInvalidFromImport(optaTeam)) {
                    if (invalidates != null)
                        invalidates.add(optaTeam);
                }
                else if (news != null) {
                    news.add(optaTeam);
                }
            }
            else if (changes != null && template.hasChanged(optaTeam)) {
                changes.add(optaTeam);
            }
        }
    }

    public static void evaluateDirtyTeams(List<OptaTeam> news, List<OptaTeam> changes, List<OptaTeam> invalidates) {
        evaluateDirtyTeams(OptaCompetition.asIds(OptaCompetition.findAllActive()), news, changes, invalidates);
    }

    public static void evaluateDirtySoccers(List<OptaPlayer> news, List<OptaPlayer> changes, List<OptaPlayer> invalidates) {
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
                        if (invalidates != null)
                            invalidates.add(optaSoccer);
                    } else if (news != null) {
                        news.add(optaSoccer);
                    }
                }
            } else if (template != null && changes != null && template.hasChanged(optaSoccer)) {
                changes.add(optaSoccer);
            }
        }
    }

    public static void evaluateDirtyMatchEvents(List<OptaMatchEvent> news, List<OptaMatchEvent> changes, List<OptaMatchEvent> invalidates) {
        Date now = GlobalDate.getCurrentDate();
        Iterable<OptaMatchEvent> matchesDirty = Model.optaMatchEvents().find("{dirty: true, matchDate: {$gte: #}}", now).as(OptaMatchEvent.class);
        for(OptaMatchEvent optaMatch : matchesDirty) {
            TemplateMatchEvent template = TemplateMatchEvent.findOneFromOptaId(optaMatch.optaMatchEventId);
            if (template == null) {
                if (TemplateMatchEvent.isInvalidFromImport(optaMatch)) {
                    if (invalidates != null)
                        invalidates.add(optaMatch);
                }
                else if (news != null) {
                    news.add(optaMatch);
                }
            }
            else if (changes != null && template.hasChanged(optaMatch)) {
                changes.add(optaMatch);
            }
        }
    }
}
