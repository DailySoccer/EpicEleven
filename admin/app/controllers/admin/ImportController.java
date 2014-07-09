package controllers.admin;

import model.Model;
import model.TemplateMatchEvent;
import model.TemplateSoccerPlayer;
import model.TemplateSoccerTeam;
import model.opta.OptaMatchEvent;
import model.opta.OptaPlayer;
import model.opta.OptaTeam;
import play.mvc.Controller;
import play.mvc.Result;

import java.util.ArrayList;
import java.util.List;

public class ImportController extends Controller {
    /**
     * IMPORT TEAMS from OPTA
     *
     */
    private static void evaluateDirtyTeams(List<OptaTeam> news, List<OptaTeam> changes, List<OptaTeam> invalidates) {
        Iterable<OptaTeam> teamsDirty = Model.optaTeams().find("{dirty: true}").as(OptaTeam.class);
        for(OptaTeam optaTeam : teamsDirty) {
            TemplateSoccerTeam template = Model.templateSoccerTeams().findOne("{optaTeamId: #}", optaTeam.optaTeamId).as(TemplateSoccerTeam.class);
            if (template == null) {
                if (TemplateSoccerTeam.isInvalid(optaTeam)) {
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

    public static int importTeams(List<OptaTeam> teams) {
        for(OptaTeam optaTeam : teams) {
            TemplateSoccerTeam.importTeam(optaTeam);
        }
        return teams.size();
    }

    public static Result showImportTeams() {
        List<OptaTeam> teamsNew = new ArrayList<>();
        List<OptaTeam> teamsChanged = new ArrayList<>();
        List<OptaTeam> teamsInvalidated = new ArrayList<>();
        evaluateDirtyTeams(teamsNew, teamsChanged, teamsInvalidated);
        return ok(views.html.import_teams.render(teamsNew, teamsChanged, teamsInvalidated));
    }

    public static Result importAllTeams() {
        List<OptaTeam> teamsNew = new ArrayList<>();
        List<OptaTeam> teamsChanged = new ArrayList<>();
        evaluateDirtyTeams(teamsNew, teamsChanged, null);

        int news = importTeams(teamsNew);
        FlashMessage.success(String.format("Imported %d teams New", news));

        int changes = importTeams(teamsChanged);
        FlashMessage.success( String.format("Imported %d teams Changed", changes) );
        return redirect(routes.ImportController.showImportTeams());
    }

    public static Result importAllNewTeams() {
        List<OptaTeam> teamsNew = new ArrayList<>();
        evaluateDirtyTeams(teamsNew, null, null);

        int news = importTeams(teamsNew);
        FlashMessage.success( String.format("Imported %d teams New", news) );
        return redirect(routes.ImportController.showImportTeams());
    }

    public static Result importAllChangedTeams() {
        List<OptaTeam> teamsChanged = new ArrayList<>();
        evaluateDirtyTeams(null, teamsChanged, null);

        int changes = importTeams(teamsChanged);
        FlashMessage.success( String.format("Imported %d teams Changed", changes) );
        return redirect(routes.ImportController.showImportTeams());
    }

    /**
     * IMPORT SOCCERS from OPTA
     *
     */
    private static void evaluateDirtySoccers(List<OptaPlayer> news, List<OptaPlayer> changes, List<OptaPlayer> invalidates) {
        Iterable<OptaPlayer> soccersDirty = Model.optaPlayers().find("{dirty: true}").as(OptaPlayer.class);
        for(OptaPlayer optaSoccer : soccersDirty) {
            TemplateSoccerPlayer template = Model.templateSoccerPlayers().findOne("{optaPlayerId: #}", optaSoccer.optaPlayerId).as(TemplateSoccerPlayer.class);
            if (template == null) {
                if (TemplateSoccerPlayer.isInvalid(optaSoccer)) {
                    if (invalidates != null)
                        invalidates.add(optaSoccer);
                }
                else if (news != null) {
                    news.add(optaSoccer);
                }
            }
            else if (changes != null && template.hasChanged(optaSoccer)) {
                changes.add(optaSoccer);
            }
        }
    }

    public static int importSoccers(List<OptaPlayer> soccers) {
        int numImported = 0;
        for(OptaPlayer optaSoccer : soccers) {
            if (TemplateSoccerPlayer.importSoccer(optaSoccer)) {
                numImported++;
            }
        }
        return numImported;
    }

    public static Result showImportSoccers() {
        List<OptaPlayer> playersNew = new ArrayList<>();
        List<OptaPlayer> playersChanged = new ArrayList<>();
        List<OptaPlayer> playersInvalidated = new ArrayList<>();
        evaluateDirtySoccers(playersNew, playersChanged, playersInvalidated);
        return ok(views.html.import_soccers.render(playersNew, playersChanged, playersInvalidated));
    }

    public static Result importAllSoccers() {
        List<OptaPlayer> playersNew = new ArrayList<>();
        List<OptaPlayer> playersChanged = new ArrayList<>();
        evaluateDirtySoccers(playersNew, playersChanged, null);

        int news = importSoccers(playersNew);
        FlashMessage.success( String.format("Imported %d soccers New", news) );

        int changes = importSoccers(playersChanged);
        FlashMessage.success( String.format("Imported %d soccers Changed", changes) );
        return redirect(routes.ImportController.showImportSoccers());
    }

    public static Result importAllNewSoccers() {
        List<OptaPlayer> playersNew = new ArrayList<>();
        evaluateDirtySoccers(playersNew, null, null);

        int news = importSoccers(playersNew);
        FlashMessage.success( String.format("Imported %d soccers New", news) );
        return redirect(routes.ImportController.showImportSoccers());
    }

    public static Result importAllChangedSoccers() {
        List<OptaPlayer> playersChanged = new ArrayList<>();
        evaluateDirtySoccers(null, playersChanged, null);

        int changes = importSoccers(playersChanged);
        FlashMessage.success( String.format("Imported %d soccers Changed", changes) );
        return redirect(routes.ImportController.showImportSoccers());
    }

    /**
     * IMPORT MATCH EVENTS from OPTA
     *
     */
    private static void evaluateDirtyMatchEvents(List<OptaMatchEvent> news, List<OptaMatchEvent> changes, List<OptaMatchEvent> invalidates) {
        Iterable<OptaMatchEvent> matchesDirty = Model.optaMatchEvents().find("{dirty: true}").as(OptaMatchEvent.class);
        for(OptaMatchEvent optaMatch : matchesDirty) {
            TemplateMatchEvent template = Model.templateMatchEvents().findOne("{optaMatchEventId: #}", optaMatch.optaMatchEventId).as(TemplateMatchEvent.class);
            if (template == null) {
                if (TemplateMatchEvent.isInvalid(optaMatch)) {
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

    public static int importMatchEvents(List<OptaMatchEvent> matches) {
        int numImported = 0;
        for(OptaMatchEvent optaMatchEvent : matches) {
            if (TemplateMatchEvent.importMatchEvent(optaMatchEvent)) {
                numImported++;
            }
        }
        return numImported;
    }

    public static Result showImportMatchEvents() {
        List<OptaMatchEvent> matchesNew = new ArrayList<>();
        List<OptaMatchEvent> matchesChanged = new ArrayList<>();
        List<OptaMatchEvent> matchesInvalidated = new ArrayList<>();
        evaluateDirtyMatchEvents(matchesNew, matchesChanged, matchesInvalidated);
        return ok(views.html.import_match_events.render(matchesNew, matchesChanged, matchesInvalidated));
    }

    public static Result importAllMatchEvents() {
        List<OptaMatchEvent> matchesNew = new ArrayList<>();
        List<OptaMatchEvent> matchesChanged = new ArrayList<>();
        evaluateDirtyMatchEvents(matchesNew, matchesChanged, null);

        int news = importMatchEvents(matchesNew);
        FlashMessage.success( String.format("Imported %d match events New", news) );

        int changes = importMatchEvents(matchesChanged);
        FlashMessage.success( String.format("Imported %d match events Changed", changes) );
        return redirect(routes.ImportController.showImportMatchEvents());
    }

    public static Result importAllNewMatchEvents() {
        List<OptaMatchEvent> matchesNew = new ArrayList<>();
        evaluateDirtyMatchEvents(matchesNew, null, null);

        int news = importMatchEvents(matchesNew);
        FlashMessage.success( String.format("Imported %d match events New", news) );
        return redirect(routes.ImportController.showImportMatchEvents());
    }

    public static Result importAllChangedMatchEvents() {
        List<OptaMatchEvent> matchesChanged = new ArrayList<>();
        evaluateDirtyMatchEvents(null, matchesChanged, null);

        int changes = importMatchEvents(matchesChanged);
        FlashMessage.success( String.format("Imported %d match events Changed", changes) );
        return redirect(routes.ImportController.showImportMatchEvents());
    }
}
