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



    public static Result writeSoccerPlayersLog() {
        Chunks<String> chunks = new StringChunks() {

            // Called when the stream is ready
            public void onReady(Chunks.Out<String> out) {
                fillLog(out);
            }

        };
        response().setHeader("Content-Disposition", "attachment; filename=log.csv");
        response().setHeader("Transfer-Encoding", "chunked");
        return ok(chunks);
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

    private static void readSalaries(SpreadsheetService service,  SpreadsheetEntry ourSpreadSheet) throws IOException, ServiceException {

        ListFeed salariesFeed = getSalariesFeed(service, ourSpreadSheet);

        HashMap<String, Integer> newSalaries = new HashMap<>();
        HashMap<String, String> newTags = new HashMap<>();

        for (ListEntry row : salariesFeed.getEntries()) {
            newSalaries.put(row.getCustomElements().getValue("optaplayerid"),
                    Integer.parseInt(row.getCustomElements().getValue("finalsalary"))); //Espacios en nombres de columnas prohibidos

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
                    List<String> tagList = Arrays.asList(newTags.get(optaPlayerId).split(",[ ]*"));
                    ArrayList<String> validTagList = new ArrayList<>();

                    for (String tag: tagList) {
                        if (TemplateSoccerPlayerTag.isValid(tag)) {
                            validTagList.add(TemplateSoccerPlayerTag.getEnum(tag).toString());
                        }
                    }

                    bdo.append("tags", validTagList);
                }

                DBObject update = new BasicDBObject("$set", bdo);
                batchWriteOperation.find(query).updateOne(update);
            }
        }

        batchWriteOperation.execute();
    }

    private static void fillLog(Chunks.Out<String> out) {

        HashMap<String, String> optaCompetitions = new HashMap<>();
        for (OptaCompetition optaCompetition: OptaCompetition.findAll()) {
            optaCompetitions.put(optaCompetition.competitionId, optaCompetition.competitionName);
        }

        out.write("id,nombre,posicion,equipo,competicion,fecha,hora,minutos,fp\n");

        HashMap<String, String> soccerTeamsMap = new HashMap<>();
        for (TemplateSoccerTeam templateSoccerTeam: TemplateSoccerTeam.findAll()) {
            soccerTeamsMap.put(templateSoccerTeam.templateSoccerTeamId.toString(), templateSoccerTeam.name);

            for (TemplateSoccerPlayer soccerPlayer: TemplateSoccerPlayer.findAllFromTemplateTeam(templateSoccerTeam.templateSoccerTeamId)) {
                    String teamName = soccerTeamsMap.containsKey(soccerPlayer.templateTeamId.toString()) ? soccerTeamsMap.get(soccerPlayer.templateTeamId.toString()) : "unknown";

                    for (SoccerPlayerStats stat : soccerPlayer.stats) {
                        out.write(soccerPlayer.optaPlayerId + ","+
                                        soccerPlayer.name+","+
                                        soccerPlayer.fieldPos.name()+","+
                                        teamName + ","+
                                        optaCompetitions.get(stat.optaCompetitionId)+","+
                                        new DateTime(stat.startDate).toString(DateTimeFormat.forPattern("dd/MM/yyyy"))+","+
                                        new DateTime(stat.startDate).toString(DateTimeFormat.forPattern("HH:mm"))+","+
                                        Integer.toString(stat.playedMinutes)+","+
                                        Integer.toString(stat.fantasyPoints)+"\n"
                        );



                }

            }


        }
        out.close();

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