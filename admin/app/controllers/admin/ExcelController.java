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
import org.apache.poi.openxml4j.exceptions.InvalidFormatException;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormat;
import play.Logger;
import play.mvc.Controller;
import play.mvc.Http;
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
        return ok(views.html.googledocs.render());
    }


    public static Result googleOAuth() {
        return index();
    }



    public static Result googleOAuth2() {
        return index();
    }



    public static Result writeSoccerPlayersLog() throws IOException {

        fillLog();

        FileInputStream input = new FileInputStream(new File("workbook.xlsx"));

        response().setHeader("Content-Disposition", "attachment; filename=log.xlsx");

        return ok(input);
    }



    public static Result upload() {
        Http.MultipartFormData body = request().body().asMultipartFormData();
        Http.MultipartFormData.FilePart newSalariesFile = body.getFile("picture");
        if (newSalariesFile != null) {
            File file = newSalariesFile.getFile();
            parseSalariesFile(file);
            return ok(views.html.googledocs.render());

        } else {
            flash("error", "Missing file");
            return ok(views.html.googledocs.render());
        }
    }



    private static void parseSalariesFile(File file) {

        Logger.debug("Parse salaries");

        Workbook wb = null; // XSSFWorkbook. (inp);
        try {
            wb = WorkbookFactory.create(file);
        } catch (IOException e) {
            Logger.error("WTF 89532");
        } catch (InvalidFormatException e) {
            Logger.error("WTF 81952");
        }

        try {
            XSSFSheet sheet = (XSSFSheet) wb.getSheet("Salarios calculados"); //getSheetAt(0);
            Logger.debug("Hay salaries");

            HashMap<String, Integer> newSalaries = new HashMap<>();
            HashMap<String, String> newTags = new HashMap<>();

            Row myRow;

            for (int i = 0; i< sheet.getLastRowNum(); i++) {
                myRow = sheet.getRow(i);

                newSalaries.put(myRow.getCell(0).getStringCellValue(), (int)myRow.getCell(2).getNumericCellValue()); //Espacios en nombres de columnas prohibidos
                newTags.put(myRow.getCell(0).getStringCellValue(), myRow.getCell(4).getStringCellValue().trim());

            }

            BatchWriteOperation batchWriteOperation = new BatchWriteOperation(Model.templateSoccerPlayers().getDBCollection().initializeUnorderedBulkOperation());

            for (String optaPlayerId : newSalaries.keySet()) {

                if (!TemplateSoccerPlayer.exists(optaPlayerId)) {
                    Logger.error("Se ha detectado un futbolista {} en el excel que no existe en la base de datos", optaPlayerId);
                    continue;
                }

                DBObject query = new BasicDBObject("optaPlayerId", optaPlayerId);
                BasicDBObject bdo = new BasicDBObject("salary", newSalaries.get(optaPlayerId));

                List<String> tagList = newTags.get(optaPlayerId) != null && !newTags.get(optaPlayerId).equals("") ?
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
        catch (NullPointerException e) {
            Logger.error("WTF 1252: No hay hoja Salarios calculados");
        }
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

        XSSFSheet pivotSheet = (XSSFSheet) wb.createSheet("Pivot");
        XSSFSheet emaSheet = (XSSFSheet) wb.createSheet("EMAs");
        XSSFSheet salarySheet = (XSSFSheet) wb.createSheet("Salarios calculados");


        Row row, emaRow, pivotRow, salaryRow;

        int playerRowCounter = 0;

        for (TemplateSoccerTeam templateSoccerTeam: TemplateSoccerTeam.findAll()) {
            soccerTeamsMap.put(templateSoccerTeam.templateSoccerTeamId.toString(), templateSoccerTeam.name);

            for (TemplateSoccerPlayer soccerPlayer: TemplateSoccerPlayer.findAllFromTemplateTeam(templateSoccerTeam.templateSoccerTeamId)) {
                String teamName = soccerTeamsMap.containsKey(soccerPlayer.templateTeamId.toString()) ? soccerTeamsMap.get(soccerPlayer.templateTeamId.toString()) : "unknown";

                pivotRow = pivotSheet.createRow((short)playerRowCounter);
                salaryRow = salarySheet.createRow((short)playerRowCounter);
                emaRow = emaSheet.createRow((short)playerRowCounter++);

                pivotRow.createCell(0).setCellValue(soccerPlayer.optaPlayerId);

                emaRow.createCell(0).setCellValue(soccerPlayer.optaPlayerId);
                emaRow.createCell(1).setCellValue(calcEma(soccerPlayer.stats));

                salaryRow.createCell(0).setCellValue(soccerPlayer.optaPlayerId);
                salaryRow.createCell(1).setCellValue(soccerPlayer.name);
                salaryRow.createCell(2).setCellValue(soccerPlayer.salary);
                salaryRow.createCell(3).setCellValue(getSalary(calcEma(soccerPlayer.stats)));
                salaryRow.createCell(4).setCellValue(soccerPlayer.tags.toString().substring(1,soccerPlayer.tags.toString().length()-1));


                int statCounter = 0;

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

                    pivotRow.createCell((statCounter*2)+1).setCellValue(stat.fantasyPoints);
                    pivotRow.createCell((statCounter*2)+2).setCellValue(stat.playedMinutes);

                    statCounter++;

                }

            }


        }


        try {
            FileOutputStream fileOut = new FileOutputStream("workbook.xlsx");
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

    private static double calcEma (List<SoccerPlayerStats> stats) {
        double factor = 0.2;
        double prevValue = Integer.MIN_VALUE;
        double hfactor = 0.02;
        double complemFactor = 0.8;

        for (SoccerPlayerStats stat : stats) {
            if (prevValue==Integer.MIN_VALUE) {
                prevValue = stat.fantasyPoints;
            } else {
                if (stat.fantasyPoints == 0 && stat.playedMinutes < 5) {
                    prevValue = (1-hfactor)*prevValue;
                }
                else {
                    prevValue = (factor * stat.fantasyPoints) + (complemFactor*prevValue);
                }
            }
        }
        return prevValue;

    }


    private static int getSalary(double input) {
        int maxFPS = 500;
        int minSalario = 4500;
        int maxSalario = 6700; //14500;

        int rangeSalario = (maxSalario-minSalario);

        double fps = input>1? input: 1;
        int salario = (int)((fps * rangeSalario)/maxFPS) + minSalario;

        return salario - (salario % 100);
    }


}