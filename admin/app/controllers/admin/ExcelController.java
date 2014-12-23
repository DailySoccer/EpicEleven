package controllers.admin;


import com.google.gdata.client.spreadsheet.SpreadsheetService;
import com.google.gdata.data.spreadsheet.*;
import com.google.gdata.util.ServiceException;
import com.mongodb.BasicDBObject;
import com.mongodb.DBObject;
import model.*;
import model.opta.OptaCompetition;
import org.apache.oltu.oauth2.client.OAuthClient;
import org.apache.oltu.oauth2.client.URLConnectionClient;
import org.apache.oltu.oauth2.client.request.OAuthClientRequest;
import org.apache.oltu.oauth2.client.response.OAuthJSONAccessTokenResponse;
import org.apache.oltu.oauth2.common.OAuthProviderType;
import org.apache.oltu.oauth2.common.exception.OAuthProblemException;
import org.apache.oltu.oauth2.common.exception.OAuthSystemException;
import org.apache.oltu.oauth2.common.message.types.GrantType;
import org.apache.poi.ss.usermodel.DataConsolidateFunction;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.AreaReference;
import org.apache.poi.ss.util.CellReference;
import org.apache.poi.xssf.usermodel.XSSFPivotTable;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormat;
import play.Logger;
import play.mvc.Controller;
import play.mvc.Result;
import utils.BatchWriteOperation;

import java.io.*;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
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
                        .setParameter("access_type", "offline")
                        .buildQueryMessage();
                return redirect(request.getLocationUri());
            } catch (OAuthSystemException e) {
                Logger.error("WTF 5036", e);
                return forbidden();
            }
        }
        return index();
    }


    public static String refreshToken(String refreshToken) {
        String result = "";

        if (refreshToken != null) {
            try {
                OAuthClientRequest request = OAuthClientRequest
                        .tokenProvider(OAuthProviderType.GOOGLE)
                        .setGrantType(GrantType.REFRESH_TOKEN)
                        .setClientId("779199222723-h36d9sjlliodjva1e2htb5rd2euhbao1.apps.googleusercontent.com")
                        .setClientSecret("cDI00MZJyCmp4r655ZAgy8hG")
                        .setRefreshToken(refreshToken)
                        .buildBodyMessage();

                OAuthClient oAuthClient = new OAuthClient(new URLConnectionClient());
                OAuthJSONAccessTokenResponse response2 = oAuthClient.accessToken(request);

                result = response2.getAccessToken();


            } catch (OAuthSystemException e) {
                Logger.error("WTF 5036", e);
            } catch (OAuthProblemException e) {
                Logger.error("WTF 5037", e);
            }
        }
        return result;
    }


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



    public static Result writeSoccerPlayersLog() throws IOException {

        fillLog();

        FileInputStream input = new FileInputStream(new File("workbook.xls"));

        response().setHeader("Content-Disposition", "attachment; filename=log.xls");


        return ok(input);
    }


    public static Result loadSalaries() {
        return loadSalaries(request().cookie(_googleAuthToken).value(),
                request().cookie(_googleRefreshToken).value());
}



    public static Result loadSalaries(String authToken, String refreshToken) {
        response().setCookie(_googleAuthToken, authToken);
        response().setCookie(_googleRefreshToken, refreshToken);


        try {
            SpreadsheetService service =
                    new SpreadsheetService("MySpreadsheetIntegration-v1");

            service.setAuthSubToken(authToken);

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
            // Si no tenemos autorización, refrescamos el token y volvemos a intentar
            String cookie = refreshToken(refreshToken);
            return loadSalaries(cookie, refreshToken);
        } catch (IOException e) {
            Logger.error("WTF 5296", e);
        }

        return index();

    }

    private static void readSalaries(SpreadsheetService service, SpreadsheetEntry ourSpreadSheet) throws IOException, ServiceException {

        ListFeed salariesFeed = getSalariesFeed(service, ourSpreadSheet);

        HashMap<String, Integer> newSalaries = new HashMap<>();
        HashMap<String, String> newTags = new HashMap<>();

        for (ListEntry row : salariesFeed.getEntries()) {
            newSalaries.put(row.getCustomElements().getValue("optaplayerid"),
                    Integer.parseInt(row.getCustomElements().getValue("finalsalary"))); //Espacios en nombres de columnas prohibidos

            newTags.put(row.getCustomElements().getValue("optaplayerid"),
                    row.getCustomElements().getValue("tags"));
        }

        BatchWriteOperation batchWriteOperation = new BatchWriteOperation(Model.templateSoccerPlayers().getDBCollection().initializeUnorderedBulkOperation());

        for (String optaPlayerId : newSalaries.keySet()) {

            if (!TemplateSoccerPlayer.exists(optaPlayerId)) {
                Logger.error("Se ha detectado un futbolista {} en el excel que no existe en la base de datos", optaPlayerId);
                continue;
            }

            DBObject query = new BasicDBObject("optaPlayerId", optaPlayerId);
            BasicDBObject bdo = new BasicDBObject("salary", newSalaries.get(optaPlayerId));

            List<String> tagList = newTags.get(optaPlayerId) != null?
                                       Arrays.asList(newTags.get(optaPlayerId).split(",[ ]*")) :
                                       new ArrayList<String>();

            // Si hay algun tag invalido, simplemente paramos de importar salarios (forzamos a que lo corrijan)
            for (String tag : tagList) {
                if (!TemplateSoccerPlayerTag.isValid(tag)) {
                    throw new RuntimeException("WTF 5761: Se ha encontrado un tag invalido " + tag);
                }
            }

            bdo.append("tags", tagList);

            DBObject update = new BasicDBObject("$set", bdo);
            batchWriteOperation.find(query).updateOne(update);
        }

        batchWriteOperation.execute();
    }

    private static void fillLog() {

        Workbook wb = new XSSFWorkbook();

        XSSFSheet mySheet = (XSSFSheet) wb.createSheet("Log");

        int rowCounter = 0;

        HashMap<String, String> optaCompetitions = new HashMap<>();
        for (OptaCompetition optaCompetition: OptaCompetition.findAll()) {
            optaCompetitions.put(optaCompetition.competitionId, optaCompetition.competitionName);
        }


        Row rowTitle = mySheet.createRow((short)rowCounter++);
        rowTitle.createCell(0).setCellValue("id");
        rowTitle.createCell(1).setCellValue("nombre");
        rowTitle.createCell(2).setCellValue("posicion");
        rowTitle.createCell(3).setCellValue("equipo");
        rowTitle.createCell(4).setCellValue("competicion");
        rowTitle.createCell(5).setCellValue("fecha");
        rowTitle.createCell(6).setCellValue("hora");
        rowTitle.createCell(7).setCellValue("minutos");
        rowTitle.createCell(8).setCellValue("fp");


        HashMap<String, String> soccerTeamsMap = new HashMap<>();


        Row row;
        for (TemplateSoccerTeam templateSoccerTeam: TemplateSoccerTeam.findAll()) {
            soccerTeamsMap.put(templateSoccerTeam.templateSoccerTeamId.toString(), templateSoccerTeam.name);

            for (TemplateSoccerPlayer soccerPlayer: TemplateSoccerPlayer.findAllFromTemplateTeam(templateSoccerTeam.templateSoccerTeamId)) {
                    String teamName = soccerTeamsMap.containsKey(soccerPlayer.templateTeamId.toString()) ? soccerTeamsMap.get(soccerPlayer.templateTeamId.toString()) : "unknown";

                    for (SoccerPlayerStats stat : soccerPlayer.stats) {

                        row = mySheet.createRow((short)rowCounter++);
                        row.createCell(0).setCellValue(soccerPlayer.optaPlayerId);
                        row.createCell(1).setCellValue(soccerPlayer.name);
                        row.createCell(2).setCellValue(soccerPlayer.fieldPos.name());
                        row.createCell(3).setCellValue(teamName);
                        row.createCell(4).setCellValue(optaCompetitions.get(stat.optaCompetitionId));
                        row.createCell(5).setCellValue(new DateTime(stat.startDate).toString(DateTimeFormat.forPattern("dd/MM/yyyy")));
                        row.createCell(6).setCellValue(new DateTime(stat.startDate).toString(DateTimeFormat.forPattern("HH:mm")));
                        row.createCell(7).setCellValue(stat.playedMinutes);
                        row.createCell(8).setCellValue(stat.fantasyPoints);

                    }

            }


        }


        XSSFPivotTable pivotTable = mySheet.createPivotTable(new AreaReference("A:I"), new CellReference("H5"));

        pivotTable.addRowLabel(0);
        //Sum up the second column
        pivotTable.addColumnLabel(DataConsolidateFunction.SUM, 1);
        //Set the third column as filter
        pivotTable.addColumnLabel(DataConsolidateFunction.SUM, 2);
        //Add filter on forth column
        pivotTable.addReportFilter(3);

        try {
            FileOutputStream fileOut = new FileOutputStream("workbook.xls");
            wb.write(fileOut);
            fileOut.close();

        }
        catch (FileNotFoundException e) {
            Logger.error("WTF 23126");
        }
        catch (IOException e) {
            Logger.error("WTF 21276");

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

    private final static String _googleAuthToken = "googleAuthToken";
    private final static String _googleRefreshToken = "googleRefreshToken";

    private final static String SPREADSHEET_NAME = "Epic Eleven - Salarios - LFP";

    private final static String SALARIES_WORKSHEET_NAME = "Salarios Finales";

}