package controllers.admin;


import com.google.gdata.client.spreadsheet.SpreadsheetService;
import com.google.gdata.data.PlainTextConstruct;
import com.google.gdata.data.spreadsheet.*;
import com.google.gdata.util.ServiceException;
import model.SoccerPlayerStats;
import model.TemplateSoccerPlayer;
import model.TemplateSoccerTeam;
import model.opta.*;
import org.apache.oltu.oauth2.client.OAuthClient;
import org.apache.oltu.oauth2.client.URLConnectionClient;
import org.apache.oltu.oauth2.client.request.OAuthClientRequest;
import org.apache.oltu.oauth2.client.response.OAuthJSONAccessTokenResponse;
import org.apache.oltu.oauth2.common.OAuthProviderType;
import org.apache.oltu.oauth2.common.exception.OAuthProblemException;
import org.apache.oltu.oauth2.common.exception.OAuthSystemException;
import org.apache.oltu.oauth2.common.message.types.GrantType;
import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormat;
import play.Logger;
import play.mvc.Controller;
import play.mvc.Result;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

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
        FlashMessage.success(String.format("Imported %d teams New", news));
        return redirect(routes.ImportController.showImportTeams());
    }

    public static Result importAllChangedTeams() {
        List<OptaTeam> teamsChanged = new ArrayList<>();
        OptaImportUtils.evaluateDirtyTeams(null, teamsChanged, null);

        int changes = OptaImportUtils.importTeams(teamsChanged);
        FlashMessage.success(String.format("Imported %d teams Changed", changes));
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
        FlashMessage.success(String.format("Imported %d soccers New", news));

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
                    .setScope("https://spreadsheets.google.com/feeds https://docs.google.com/feeds")
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
                    .setGrantType(GrantType.AUTHORIZATION_CODE)
                    .setClientId("779199222723-h36d9sjlliodjva1e2htb5rd2euhbao1.apps.googleusercontent.com")
                    .setClientSecret("cDI00MZJyCmp4r655ZAgy8hG")
                    .setRedirectURI("http://localhost:9000/admin/updatesalaries")
                    .setCode(code)
                    .buildBodyMessage();


            OAuthClient oAuthClient = new OAuthClient(new URLConnectionClient());
            OAuthJSONAccessTokenResponse response2 = oAuthClient.accessToken(request);


            System.out.println("\nAccess Token: " + response2.getAccessToken() + "\nExpires in: " + response2.getExpiresIn()  );

            worksheetTest(response2.getAccessToken());


        }
        catch (OAuthSystemException e) {
            Logger.error("WTF 5036", e);
            return forbidden();
        }
        //*
        catch (OAuthProblemException e) {
            Logger.error("WTF 5037", e);
            return forbidden();
        }
        //*/

        return ok("Whatever");
    }

    public static void updateSalaries(String token) {
        SpreadsheetService service = new SpreadsheetService("MySpreadsheetIntegration");
        service.setProtocolVersion(SpreadsheetService.Versions.V3);

        service.setAuthSubToken(token);

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
        //return ok("Whatever");

    }

    public static void worksheetTest(String token) {

        try {
            SpreadsheetService service =
                    new SpreadsheetService("MySpreadsheetIntegration-v1");

            service.setAuthSubToken(token);

            // TODO: Authorize the service object for a specific user (see other sections)

            // Define the URL to request.  This should never change.
            URL SPREADSHEET_FEED_URL = new URL(
                    "https://spreadsheets.google.com/feeds/spreadsheets/private/full");

            // Make a request to the API and get all spreadsheets.
            SpreadsheetFeed feed = service.getFeed(SPREADSHEET_FEED_URL,
                    SpreadsheetFeed.class);


            SpreadsheetEntry ourSpreadSheet = null;
            // Iterate through all of the spreadsheets returned
            for (SpreadsheetEntry spreadsheet : feed.getEntries()) {
                // Print the title of this spreadsheet to the screen
                if (spreadsheet.getTitle().getPlainText().equals("Epic Eleven - Salarios - LFP")) {
                    ourSpreadSheet = spreadsheet;
                    break;
                }
            }

            WorksheetFeed worksheetFeed = service.getFeed(
                    ourSpreadSheet.getWorksheetFeedUrl(), WorksheetFeed.class);

            WorksheetEntry ourWorksheet = null;
            for (WorksheetEntry worksheet : worksheetFeed.getEntries()) {
                if (worksheet.getTitle().getPlainText().equals("LOG")) {
                    ourWorksheet = worksheet;
                    ourWorksheet.setColCount(8);
                    ourWorksheet.setRowCount(1);
                    ourWorksheet.update();
                    break;
                }
            }
            if (ourWorksheet == null) {
                // Create a local representation of the new worksheet.
                ourWorksheet = new WorksheetEntry();
                ourWorksheet.setTitle(new PlainTextConstruct("LOG"));
                ourWorksheet.setColCount(8);
                ourWorksheet.setRowCount(1);

                URL worksheetFeedUrl = ourSpreadSheet.getWorksheetFeedUrl();
                service.insert(worksheetFeedUrl, ourWorksheet);
            }


            // Fetch the cell feed of the worksheet.
            CellFeed cellFeed = service.getFeed(ourWorksheet.getCellFeedUrl(), CellFeed.class);

            // Iterate through each cell, updating its value if necessary.
            for (CellEntry cell : cellFeed.getEntries()) {
                if (cell.getTitle().getPlainText().equals("A1")) {
                    cell.changeInputValueLocal("id");
                    cell.update();
                } else if (cell.getTitle().getPlainText().equals("B1")) {
                    cell.changeInputValueLocal("nombre");
                    cell.update();
                } else if (cell.getTitle().getPlainText().equals("C1")) {
                    cell.changeInputValueLocal("posicion");
                    cell.update();
                } else if (cell.getTitle().getPlainText().equals("D1")) {
                    cell.changeInputValueLocal("equipo");
                    cell.update();
                } else if (cell.getTitle().getPlainText().equals("E1")) {
                    cell.changeInputValueLocal("competicion");
                    cell.update();
                } else if (cell.getTitle().getPlainText().equals("F1")) {
                    cell.changeInputValueLocal("fecha");
                    cell.update();
                } else if (cell.getTitle().getPlainText().equals("G1")) {
                    cell.changeInputValueLocal("hora");
                    cell.update();
                } else if (cell.getTitle().getPlainText().equals("H1")) {
                    cell.changeInputValueLocal("fp");
                    cell.update();
                }
            }

            // Fetch the list feed of the worksheet.
            URL listFeedUrl = ourWorksheet.getListFeedUrl();

            ListFeed listFeed = service.getFeed(listFeedUrl, ListFeed.class);

            List <TemplateSoccerPlayer> soccerPlayers = TemplateSoccerPlayer.findAll();

            HashMap<String, String> soccerTeamsMap = new HashMap<>();
            for (TemplateSoccerTeam templateSoccerTeam: TemplateSoccerTeam.findAll()) {
                soccerTeamsMap.put(templateSoccerTeam.templateSoccerTeamId.toString(), templateSoccerTeam.name);
            }

            HashMap<String, String> optaCompetitions = new HashMap<>();
            for (OptaCompetition optaCompetition: OptaCompetition.findAll()) {
                optaCompetitions.put(optaCompetition.competitionId, optaCompetition.competitionName);
            }

            for (TemplateSoccerPlayer soccerPlayer: soccerPlayers) {
                String teamName = soccerTeamsMap.containsKey(soccerPlayer.templateTeamId.toString()) ? soccerTeamsMap.get(soccerPlayer.templateTeamId.toString()) : "unknown";

                for (SoccerPlayerStats stat: soccerPlayer.stats) {
                    // Create a local representation of the new row.
                    ListEntry row = new ListEntry();

                    row.getCustomElements().setValueLocal("id", soccerPlayer.optaPlayerId);
                    row.getCustomElements().setValueLocal("nombre", soccerPlayer.name);
                    row.getCustomElements().setValueLocal("posicion", soccerPlayer.fieldPos.name());
                    row.getCustomElements().setValueLocal("equipo", teamName);
                    row.getCustomElements().setValueLocal("competicion", optaCompetitions.get(stat.optaCompetitionId));
                    row.getCustomElements().setValueLocal("fecha", new DateTime(stat.startDate).toString(DateTimeFormat.forPattern("dd/MM/yyyy")) );
                    row.getCustomElements().setValueLocal("hora", new DateTime(stat.startDate).toString(DateTimeFormat.forPattern("HH:mm")) );
                    row.getCustomElements().setValueLocal("fp", Integer.toString(stat.fantasyPoints));

                    // Send the new row to the API for insertion.
                    row = service.insert(listFeedUrl, row);
                }

            }




        } catch (MalformedURLException e) {
            Logger.error("WTF 5096", e);
        } catch (ServiceException e) {
            Logger.error("WTF 5196", e);
            e.printStackTrace();
        } catch (IOException e) {
            Logger.error("WTF 5296", e);
        }
    }


}
