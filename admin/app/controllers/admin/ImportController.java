package controllers.admin;


import com.google.gdata.client.spreadsheet.SpreadsheetService;
import com.google.gdata.data.spreadsheet.SpreadsheetEntry;
import com.google.gdata.data.spreadsheet.SpreadsheetFeed;
import com.google.gdata.util.ServiceException;
import model.opta.*;
import org.apache.oltu.oauth2.client.request.OAuthClientRequest;
import org.apache.oltu.oauth2.common.OAuthProviderType;
import org.apache.oltu.oauth2.common.exception.OAuthSystemException;
import org.apache.oltu.oauth2.common.message.types.GrantType;
import play.Logger;
import play.libs.F;
import play.libs.ws.WS;
import play.libs.ws.WSResponse;
import play.mvc.Controller;
import play.mvc.Result;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.*;

public class ImportController extends Controller {
    /**
     * IMPORT TEAMS from OPTA
     *
     */
    public static Result showImportTeams() {
        List<String> competitionsActivated = OptaCompetition.asIds(OptaCompetition.findAllActive());
        List<OptaTeam> teamsNew = new ArrayList<>();
        List<OptaTeam> teamsChanged = new ArrayList<>();
        List<OptaTeam> teamsInvalidated = new ArrayList<>();
        OptaImportUtils.evaluateDirtyTeams(competitionsActivated, teamsNew, teamsChanged, teamsInvalidated);
        return ok(views.html.import_teams.render(teamsNew, teamsChanged, teamsInvalidated, "*", OptaCompetition.asMap(OptaCompetition.findAllActive())));
    }

    public static Result showImportTeamsFromCompetition(String competitionId) {
        List<String> competitionsSelected = new ArrayList<>();
        competitionsSelected.add(competitionId);

        List<OptaTeam> teamsNew = new ArrayList<>();
        List<OptaTeam> teamsChanged = new ArrayList<>();
        List<OptaTeam> teamsInvalidated = new ArrayList<>();
        OptaImportUtils.evaluateDirtyTeams(competitionsSelected, teamsNew, teamsChanged, teamsInvalidated);
        return ok(views.html.import_teams.render(teamsNew, teamsChanged, teamsInvalidated, competitionId, OptaCompetition.asMap(OptaCompetition.findAllActive())));
    }

    public static Result importAllTeams() {
        List<OptaTeam> teamsNew = new ArrayList<>();
        List<OptaTeam> teamsChanged = new ArrayList<>();
        OptaImportUtils.evaluateDirtyTeams(teamsNew, teamsChanged, null);

        int news = OptaImportUtils.importTeams(teamsNew);
        FlashMessage.success(String.format("Imported %d teams New", news));

        int changes = OptaImportUtils.importTeams(teamsChanged);
        FlashMessage.success( String.format("Imported %d teams Changed", changes) );
        return redirect(routes.ImportController.showImportTeams());
    }

    public static Result importAllNewTeams() {
        List<OptaTeam> teamsNew = new ArrayList<>();
        OptaImportUtils.evaluateDirtyTeams(teamsNew, null, null);

        int news = OptaImportUtils.importTeams(teamsNew);
        FlashMessage.success( String.format("Imported %d teams New", news) );
        return redirect(routes.ImportController.showImportTeams());
    }

    public static Result importAllNewTeamsFromCompetition(String competitionId) {
        List<String> competitionsSelected = new ArrayList<>();
        competitionsSelected.add(competitionId);

        List<OptaTeam> teamsNew = new ArrayList<>();
        OptaImportUtils.evaluateDirtyTeams(competitionsSelected, teamsNew, null, null);

        int news = OptaImportUtils.importTeams(teamsNew);
        FlashMessage.success( String.format("Imported %d teams New", news) );
        return redirect(routes.ImportController.showImportTeams());
    }

    public static Result importAllChangedTeams() {
        List<OptaTeam> teamsChanged = new ArrayList<>();
        OptaImportUtils.evaluateDirtyTeams(null, teamsChanged, null);

        int changes = OptaImportUtils.importTeams(teamsChanged);
        FlashMessage.success( String.format("Imported %d teams Changed", changes) );
        return redirect(routes.ImportController.showImportTeams());
    }

    /**
     * IMPORT SOCCERS from OPTA
     *
     */
    public static Result showImportSoccers() {
        List<OptaPlayer> playersNew = new ArrayList<>();
        List<OptaPlayer> playersChanged = new ArrayList<>();
        List<OptaPlayer> playersInvalidated = new ArrayList<>();
        OptaImportUtils.evaluateDirtySoccers(playersNew, playersChanged, playersInvalidated);
        return ok(views.html.import_soccers.render(playersNew, playersChanged, playersInvalidated));
    }

    public static Result importAllSoccers() {
        List<OptaPlayer> playersNew = new ArrayList<>();
        List<OptaPlayer> playersChanged = new ArrayList<>();
        OptaImportUtils.evaluateDirtySoccers(playersNew, playersChanged, null);

        int news = OptaImportUtils.importPlayers(playersNew);
        FlashMessage.success( String.format("Imported %d soccers New", news) );

        int changes = OptaImportUtils.importPlayers(playersChanged);
        FlashMessage.success( String.format("Imported %d soccers Changed", changes) );
        return redirect(routes.ImportController.showImportSoccers());
    }

    public static Result importAllNewSoccers() {
        List<OptaPlayer> playersNew = new ArrayList<>();
        OptaImportUtils.evaluateDirtySoccers(playersNew, null, null);

        int news = OptaImportUtils.importPlayers(playersNew);
        FlashMessage.success( String.format("Imported %d soccers New", news) );
        return redirect(routes.ImportController.showImportSoccers());
    }

    public static Result importAllChangedSoccers() {
        List<OptaPlayer> playersChanged = new ArrayList<>();
        OptaImportUtils.evaluateDirtySoccers(null, playersChanged, null);

        int changes = OptaImportUtils.importPlayers(playersChanged);
        FlashMessage.success( String.format("Imported %d soccers Changed", changes) );
        return redirect(routes.ImportController.showImportSoccers());
    }

    /**
     * IMPORT MATCH EVENTS from OPTA
     *
     */
    public static Result showImportMatchEvents() {
        List<OptaMatchEvent> matchesNew = new ArrayList<>();
        List<OptaMatchEvent> matchesChanged = new ArrayList<>();
        List<OptaMatchEvent> matchesInvalidated = new ArrayList<>();
        OptaImportUtils.evaluateDirtyMatchEvents(matchesNew, matchesChanged, matchesInvalidated);
        return ok(views.html.import_match_events.render(matchesNew, matchesChanged, matchesInvalidated, OptaTeam.findAllAsMap()));
    }

    public static Result importAllMatchEvents() {
        List<OptaMatchEvent> matchesNew = new ArrayList<>();
        List<OptaMatchEvent> matchesChanged = new ArrayList<>();
        OptaImportUtils.evaluateDirtyMatchEvents(matchesNew, matchesChanged, null);

        int news = OptaImportUtils.importMatchEvents(matchesNew);
        FlashMessage.success( String.format("Imported %d match events New", news) );

        int changes = OptaImportUtils.importMatchEvents(matchesChanged);
        FlashMessage.success( String.format("Imported %d match events Changed", changes) );
        return redirect(routes.ImportController.showImportMatchEvents());
    }

    public static Result importAllNewMatchEvents() {
        List<OptaMatchEvent> matchesNew = new ArrayList<>();
        OptaImportUtils.evaluateDirtyMatchEvents(matchesNew, null, null);

        int news = OptaImportUtils.importMatchEvents(matchesNew);
        FlashMessage.success( String.format("Imported %d match events New", news) );
        return redirect(routes.ImportController.showImportMatchEvents());
    }

    public static Result importAllChangedMatchEvents() {
        List<OptaMatchEvent> matchesChanged = new ArrayList<>();
        OptaImportUtils.evaluateDirtyMatchEvents(null, matchesChanged, null);

        int changes = OptaImportUtils.importMatchEvents(matchesChanged);
        FlashMessage.success( String.format("Imported %d match events Changed", changes) );
        return redirect(routes.ImportController.showImportMatchEvents());
    }

    public static Result exportSalaries() {
        ProcessBuilder pb = new ProcessBuilder("./export_salaries.sh", "salaries.csv");
        String pwd = pb.environment().get("PWD");
        ProcessBuilder data = pb.directory(new File(pwd+"/data"));
        try {
            Process p = data.start();
            p.waitFor();
        }
        catch (IOException e) {
            Logger.error("WTF 1125", e);
        }
        catch (InterruptedException e) {
            Logger.error("WTF 1135", e);
        }
        return redirect(routes.DashboardController.index());
    }

    public static Result importSalaries() {
        ProcessBuilder pb = new ProcessBuilder("./import_salaries.sh", "salaries.csv");
        String pwd = pb.environment().get("PWD");
        ProcessBuilder data = pb.directory(new File(pwd+"/data"));
        try {
            Process p = data.start();
            p.waitFor();
        }
        catch (IOException e) {
            Logger.error("WTF 1126", e);
        }
        catch (InterruptedException e) {
            Logger.error("WTF 1136", e);
        }
        return redirect(routes.DashboardController.index());
    }

    public static Result googleOAuth() {
        try {
            OAuthClientRequest request = OAuthClientRequest
                    .authorizationProvider(OAuthProviderType.GOOGLE)
                    .setClientId("779199222723-h36d9sjlliodjva1e2htb5rd2euhbao1.apps.googleusercontent.com")
                    .setRedirectURI("http://localhost:9000/admin/updatesalaries")
                    .setResponseType("code")
                    .setScope("https://spreadsheets.google.com/feeds")
                    .buildQueryMessage();
            return redirect(request.getLocationUri());
        } catch (OAuthSystemException e) {
            Logger.error("WTF 5036", e);
            return forbidden();
        }
    }

    public static Result googleOAuth2() {
        String code = request().getQueryString("code");

        try {
            OAuthClientRequest request = OAuthClientRequest
                    .tokenProvider(OAuthProviderType.GOOGLE)
                    .setCode(code)
                    .setClientId("779199222723-h36d9sjlliodjva1e2htb5rd2euhbao1.apps.googleusercontent.com")
                    .setClientSecret("cDI00MZJyCmp4r655ZAgy8hG")
                    .setRedirectURI("http://localhost:9000/admin/updatesalaries2")
                    .setGrantType(GrantType.AUTHORIZATION_CODE)
                    .buildBodyMessage();


            Logger.info(request.getLocationUri().toString());
            F.Promise<WSResponse> responsePromise = WS.url(request.getLocationUri()).post(request.getBody());
            WSResponse response = responsePromise.get(10000);
            Logger.info(response.getBody());


        } catch (OAuthSystemException e) {
            Logger.error("WTF 5036", e);
            return forbidden();
        }

        return ok("Whatever");
    }

    public static Result updateSalaries() {
        SpreadsheetService service = new SpreadsheetService("MySpreadsheetIntegration");
        service.setProtocolVersion(SpreadsheetService.Versions.V3);

        service.setAuthSubToken(request().getQueryString("token"));

        // TODO: Authorize the service object for a specific user (see other sections)

        // Define the URL to request.  This should never change.

        try {
            // Define the URL to request.  This should never change.
            URL SPREADSHEET_FEED_URL = new URL("https://spreadsheets.google.com/feeds/spreadsheets/private/full");

            // Make a request to the API and get all spreadsheets.
            SpreadsheetFeed feed = service.getFeed(SPREADSHEET_FEED_URL, SpreadsheetFeed.class);
            List<SpreadsheetEntry> spreadsheets = feed.getEntries();

            // Iterate through all of the spreadsheets returned
            for (SpreadsheetEntry spreadsheet : spreadsheets) {
                // Print the title of this spreadsheet to the screen
                Logger.info(spreadsheet.getTitle().getPlainText());
            }


        } catch (MalformedURLException e) {
            Logger.error("WTF 5096", e);
        } catch (ServiceException e) {
            Logger.error("WTF 5196", e);
        } catch (IOException e) {
            Logger.error("WTF 5296", e);
        }
        return ok("Whatever");

    }

}
