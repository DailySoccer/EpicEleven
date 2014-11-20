package controllers.admin;


import com.google.gdata.client.spreadsheet.SpreadsheetService;
import com.google.gdata.data.PlainTextConstruct;
import com.google.gdata.data.spreadsheet.*;
import com.google.gdata.util.ServiceException;
import com.mongodb.BasicDBObject;
import com.mongodb.DBObject;
import model.Model;
import model.SoccerPlayerStats;
import model.TemplateSoccerPlayer;
import model.TemplateSoccerTeam;
import model.opta.OptaCompetition;
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
import utils.BatchWriteOperation;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;
import java.util.List;

public class ExcelController extends Controller {

    public static Result index() {
        if (request().cookie(_googleAuthToken) == null)
            return googleOAuth();

        return ok(views.html.googledocs.render());
    }


    public static Result googleOAuth() {
        if (request().cookie(_googleAuthToken) == null) {
            try {
                OAuthClientRequest request = OAuthClientRequest
                        .authorizationProvider(OAuthProviderType.GOOGLE)
                        .setClientId("779199222723-h36d9sjlliodjva1e2htb5rd2euhbao1.apps.googleusercontent.com")
                        .setRedirectURI("http://localhost:9000/admin/googledocs/authn")
                        .setResponseType("code")
                        .setScope("https://spreadsheets.google.com/feeds https://docs.google.com/feeds")
                        .buildQueryMessage();
                return redirect(request.getLocationUri());
            } catch (OAuthSystemException e) {
                Logger.error("WTF 5036", e);
                return forbidden();
            }
        }
        return index();
    }

    /**
    public static void getExpiration(String token) {
        https://www.googleapis.com/oauth2/v1/tokeninfo?access_token=

    }
    */

    public static Result refreshToken() {
        if (request().cookie(_googleRefreshToken) != null) {
            try {
                OAuthClientRequest request = OAuthClientRequest
                        .tokenProvider(OAuthProviderType.GOOGLE)
                        .setGrantType(GrantType.REFRESH_TOKEN)
                        .setClientId("779199222723-h36d9sjlliodjva1e2htb5rd2euhbao1.apps.googleusercontent.com")
                        .setClientSecret("cDI00MZJyCmp4r655ZAgy8hG")
                        .setRefreshToken(request().cookie(_googleRefreshToken).value())
                        .buildBodyMessage();

                OAuthClient oAuthClient = new OAuthClient(new URLConnectionClient());
                OAuthJSONAccessTokenResponse response2 = oAuthClient.accessToken(request);


                Logger.info("\nAccess Token: " + response2.getAccessToken() + "\nExpires in: " + response2.getExpiresIn());

                response().setCookie(_googleAuthToken, response2.getAccessToken());
                response().setCookie(_googleRefreshToken, response2.getRefreshToken());


            } catch (OAuthSystemException e) {
                Logger.error("WTF 5036", e);
            } catch (OAuthProblemException e) {
                Logger.error("WTF 5037", e);
            }
        }
        else return googleOAuth();
        return ok();
    }

    public static Result googleOAuth2() {
        String _code = request().getQueryString("code");

        try {
            OAuthClientRequest request = OAuthClientRequest
                    .tokenProvider(OAuthProviderType.GOOGLE)
                    .setGrantType(GrantType.AUTHORIZATION_CODE)
                    .setClientId("779199222723-h36d9sjlliodjva1e2htb5rd2euhbao1.apps.googleusercontent.com")
                    .setClientSecret("cDI00MZJyCmp4r655ZAgy8hG")
                    .setRedirectURI("http://localhost:9000/admin/googledocs/authn")
                    .setCode(_code)
                    .buildBodyMessage();


            OAuthClient oAuthClient = new OAuthClient(new URLConnectionClient());
            OAuthJSONAccessTokenResponse response2 = oAuthClient.accessToken(request);

            response().setCookie(_googleAuthToken, response2.getAccessToken());
            if (response2.getRefreshToken() != null)
                response().setCookie(_googleRefreshToken, response2.getRefreshToken());


            Logger.info("\nAccess Token: " + response2.getAccessToken() + "\nExpires in: " + response2.getExpiresIn());
        }
        catch (OAuthSystemException e) {
            Logger.error("WTF 5038", e);
            return forbidden();
        }

        catch (OAuthProblemException e) {
            Logger.error("WTF 5039", e);
            return forbidden();
        }

        return index();
    }

    public static Result writeSoccerPlayersLog() {
        try {
            SpreadsheetService service =
                    new SpreadsheetService("MySpreadsheetIntegration-v1");

            service.setAuthSubToken(request().cookie(_googleAuthToken).value());

            // TODO: Authorize the service object for a specific user (see other sections)

            // Define the URL to request.  This should never change.
            URL SPREADSHEET_FEED_URL = new URL(
                    "https://spreadsheets.google.com/feeds/spreadsheets/private/full");

            // Make a request to the API and get all spreadsheets.
            SpreadsheetFeed feed = service.getFeed(SPREADSHEET_FEED_URL,
                    SpreadsheetFeed.class);

            SpreadsheetEntry ourSpreadSheet = getSpreadsheet(feed);

            getLastLog(service, ourSpreadSheet);

            WorksheetEntry ourWorksheet = resetLog(service, ourSpreadSheet);

            fillTitleCells(service, ourWorksheet);

            fillLog(service, ourWorksheet);

            updateLastLog(service, ourSpreadSheet);

        } catch (MalformedURLException e) {
            Logger.error("WTF 5096", e);
        } catch (ServiceException e) {
            Logger.error("WTF 5196", e);
            e.printStackTrace();
        } catch (IOException e) {
            Logger.error("WTF 5296", e);
        }

        return index();
    }


    public static Result loadSalaries() {

        try {
            SpreadsheetService service =
                    new SpreadsheetService("MySpreadsheetIntegration-v1");

            service.setAuthSubToken(request().cookie(_googleAuthToken).value());

            // TODO: Authorize the service object for a specific user (see other sections)

            // Define the URL to request.  This should never change.
            URL SPREADSHEET_FEED_URL = new URL(
                    "https://spreadsheets.google.com/feeds/spreadsheets/private/full");

            // Make a request to the API and get all spreadsheets.
            SpreadsheetFeed feed = service.getFeed(SPREADSHEET_FEED_URL,
                    SpreadsheetFeed.class);

            SpreadsheetEntry ourSpreadSheet = getSpreadsheet(feed);

            readSalaries(service, ourSpreadSheet);

        } catch (MalformedURLException e) {
            Logger.error("WTF 5096", e);
        } catch (ServiceException e) {
            Logger.error("WTF 5196", e);
            e.printStackTrace();
        } catch (IOException e) {
            Logger.error("WTF 5296", e);
        }

        return index();

    }

    private static void readSalaries(SpreadsheetService service,  SpreadsheetEntry ourSpreadSheet) throws IOException, ServiceException {

        ListFeed salariesFeed = getSalariesFeed(service, ourSpreadSheet);

        HashMap<String, Integer> newSalaries = new HashMap<>();
        HashMap<String, String> newTags = new HashMap<>();

        for (ListEntry row : salariesFeed.getEntries()) {
            newSalaries.put(row.getCustomElements().getValue("optaplayerid"),
                            Integer.parseInt(row.getCustomElements().getValue("salary")));

            newTags.put(row.getCustomElements().getValue("optaplayerid"),
                        row.getCustomElements().getValue("tags"));
        }

        HashMap<String, TemplateSoccerPlayer> soccerPlayers = TemplateSoccerPlayer.findAllAsMap();

        BatchWriteOperation batchWriteOperation = new BatchWriteOperation(Model.templateSoccerPlayers().getDBCollection().initializeUnorderedBulkOperation());


        for (String optaPlayerId: newSalaries.keySet()) {

            if (soccerPlayers.containsKey(optaPlayerId)) {

                DBObject query = new BasicDBObject("optaPlayerId", optaPlayerId);
                BasicDBObject bdo = new BasicDBObject("salary", newSalaries.get(optaPlayerId));

                if (newTags.containsKey(optaPlayerId) && newTags.get(optaPlayerId)!=null) {
                    String[] tagsArray = newTags.get(optaPlayerId).split(",");
                    for (int i = 0; i<tagsArray.length; i++) {
                        tagsArray[i] = tagsArray[i].trim();
                    }
                    bdo.append("tags", tagsArray);
                }

                DBObject update = new BasicDBObject("$set", bdo);
                batchWriteOperation.find(query).updateOne(update);
            }
        }

        batchWriteOperation.execute();
    }

    private static void fillLog(SpreadsheetService service, WorksheetEntry ourWorksheet) throws IOException, ServiceException {
        // Fetch the list feed of the worksheet.
        URL listFeedUrl = ourWorksheet.getListFeedUrl();

        ListFeed listFeed = service.getFeed(listFeedUrl, ListFeed.class);

        List<TemplateSoccerPlayer> soccerPlayers = TemplateSoccerPlayer.findAll();

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
                if (_lastLogDate.isBefore(stat.startDate.getTime())) {
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

        }
    }

    private static SpreadsheetEntry getSpreadsheet(SpreadsheetFeed feed) {
        SpreadsheetEntry ourSpreadSheet = null;
        // Iterate through all of the spreadsheets returned
        for (SpreadsheetEntry spreadsheet : feed.getEntries()) {
            // Print the title of this spreadsheet to the screen
            if (spreadsheet.getTitle().getPlainText().equals(SPREADSHEET_NAME)) {
                ourSpreadSheet = spreadsheet;
                break;
            }
        }
        return ourSpreadSheet;
    }

    private static WorksheetEntry resetLog(SpreadsheetService service, SpreadsheetEntry ourSpreadSheet) throws IOException, ServiceException {
        WorksheetFeed worksheetFeed = service.getFeed(
                ourSpreadSheet.getWorksheetFeedUrl(), WorksheetFeed.class);

        WorksheetEntry ourWorksheet = null;
        for (WorksheetEntry worksheet : worksheetFeed.getEntries()) {
            if (worksheet.getTitle().getPlainText().equals(LOG_WORKSHEET_NAME)) {
                ourWorksheet = worksheet;
                break;
            }
        }

        if (ourWorksheet == null) {
            // Create a local representation of the new worksheet.
            ourWorksheet = new WorksheetEntry();
            ourWorksheet.setTitle(new PlainTextConstruct(LOG_WORKSHEET_NAME));
            ourWorksheet.setColCount(8);
            ourWorksheet.setRowCount(1);

            URL worksheetFeedUrl = ourSpreadSheet.getWorksheetFeedUrl();
            service.insert(worksheetFeedUrl, ourWorksheet);
        }

        return ourWorksheet;
    }

    private static ListFeed getSalariesFeed(SpreadsheetService service, SpreadsheetEntry ourSpreadSheet) throws IOException, ServiceException {
        WorksheetFeed worksheetFeed = service.getFeed(
                ourSpreadSheet.getWorksheetFeedUrl(), WorksheetFeed.class);

        WorksheetEntry ourWorksheet = new WorksheetEntry();
        for (WorksheetEntry worksheet : worksheetFeed.getEntries()) {
            if (worksheet.getTitle().getPlainText().equals(SALARIES_WORKSHEET_NAME)) {
                ourWorksheet = worksheet;
                break;
            }
        }
        return service.getFeed(ourWorksheet.getListFeedUrl(), ListFeed.class);
    }


    private static ListFeed getLastLogFeed(SpreadsheetService service, SpreadsheetEntry ourSpreadSheet) throws IOException, ServiceException {
        return service.getFeed(getLastLogWorksheet(service, ourSpreadSheet).getListFeedUrl(), ListFeed.class);
    }

    private static WorksheetEntry getLastLogWorksheet(SpreadsheetService service, SpreadsheetEntry ourSpreadSheet) throws IOException, ServiceException {
        WorksheetFeed worksheetFeed = service.getFeed(
                ourSpreadSheet.getWorksheetFeedUrl(), WorksheetFeed.class);

        WorksheetEntry ourWorksheet = null;
        for (WorksheetEntry worksheet : worksheetFeed.getEntries()) {
            if (worksheet.getTitle().getPlainText().equals(LASTLOG_WORKSHEET_NAME)) {
                ourWorksheet = worksheet;
                break;
            }
        }
        return ourWorksheet;
    }

    private static void fillTitleCells(SpreadsheetService service, WorksheetEntry ourWorksheet) throws IOException, ServiceException {
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
    }

    private static DateTime getLastLog(SpreadsheetService service,  SpreadsheetEntry ourSpreadSheet) throws IOException, ServiceException {

        ListFeed feed = getLastLogFeed(service, ourSpreadSheet);

        for (ListEntry row : feed.getEntries()) {
            _lastLogDate = DateTime.parse(row.getCustomElements().getValue("hora")+" "+
                                          row.getCustomElements().getValue("fecha"),
                                          DateTimeFormat.forPattern("HH:mm dd/MM/yyyy"));
        }

        return _lastLogDate;
    }

    private static void updateLastLog(SpreadsheetService service,  SpreadsheetEntry ourSpreadSheet) throws IOException, ServiceException {
        WorksheetEntry ourWorksheet = getLastLogWorksheet(service, ourSpreadSheet);

        ourWorksheet.setColCount(2);
        ourWorksheet.setRowCount(1);
        ourWorksheet.update();

        URL listFeedUrl = ourWorksheet.getListFeedUrl();

        ListEntry row = new ListEntry();

        row.getCustomElements().setValueLocal("fecha", new DateTime().toString("dd/MM/yyyy"));
        row.getCustomElements().setValueLocal("hora", new DateTime().toString("HH:mm"));

        service.insert(listFeedUrl, row);
    }



    private static DateTime _lastLogDate = new DateTime(1970, 1, 1, 0, 0);

    private final static String _googleAuthToken = "googleAuthToken";
    private final static String _googleRefreshToken = "googleRefreshToken";

    private final static String SPREADSHEET_NAME = "Epic Eleven - Salarios - LFP";
    private final static String LOG_WORKSHEET_NAME = "LOG";
    private final static String LASTLOG_WORKSHEET_NAME = "Last LOG";
    private final static String SALARIES_WORKSHEET_NAME = "Salarios";

}