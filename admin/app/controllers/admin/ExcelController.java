package controllers.admin;


import com.mongodb.BasicDBObject;
import com.mongodb.DBObject;
import model.*;
import model.opta.OptaCompetition;
import org.apache.poi.openxml4j.exceptions.InvalidFormatException;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

public class ExcelController extends Controller {

    public static Result index() {
        return ok(views.html.excel.render());
    }


    public static Result writeSoccerPlayersLog() throws IOException {

        fillLog();

        FileInputStream input = new FileInputStream(new File(_FILENAME));

        response().setHeader("Content-Disposition", "attachment; filename=log.xlsx");

        return ok(input);
    }



    public static Result upload() {
        Http.MultipartFormData body = request().body().asMultipartFormData();
        Http.MultipartFormData.FilePart newSalariesFile = body.getFile("excel");
        if (newSalariesFile != null) {
            parseSalariesFile(newSalariesFile.getFile());
            return ok(views.html.excel.render());

        } else {
            flash("error", "Missing file");
            return ok(views.html.excel.render());
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
            XSSFSheet sheet = (XSSFSheet) wb.getSheet(_SALARIES); //getSheetAt(0);

            HashMap<String, Integer> newSalaries = new HashMap<>();
            HashMap<String, String> newTags = new HashMap<>();

            Row myRow;

            // Empezamos desde el 1 para saltarnos la fila título
            for (int i = 1; i<= sheet.getLastRowNum(); i++) {
                myRow = sheet.getRow(i);

                newSalaries.put(myRow.getCell(0).getStringCellValue(), (int)myRow.getCell(4).getNumericCellValue()); //Espacios en nombres de columnas prohibidos
                newTags.put(myRow.getCell(0).getStringCellValue(), myRow.getCell(5).getStringCellValue().trim());

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
            Logger.error("WTF 1252: No hay hoja Salaries");
        }
    }


    private static void fillLogRowTitle(Sheet sheet) {
        Row rowTitle = sheet.createRow((short)0);
        rowTitle.createCell(0).setCellValue(_OPTA_PLAYER_ID);
        rowTitle.createCell(1).setCellValue(_NAME);
        rowTitle.createCell(2).setCellValue(_POSITION);
        rowTitle.createCell(3).setCellValue(_TEAM);
        rowTitle.createCell(4).setCellValue(_COMPETITION);
        rowTitle.createCell(5).setCellValue(_DATE);
        rowTitle.createCell(6).setCellValue(_TIME);
        rowTitle.createCell(7).setCellValue(_MINUTES_PLAYED);
        rowTitle.createCell(8).setCellValue(_FANTASY_POINTS);
    }


    private static void fillSalaryRowTitle(Sheet sheet) {
        Row salaryRow = sheet.createRow((short)0);
        salaryRow.createCell(0).setCellValue(_OPTA_PLAYER_ID);
        salaryRow.createCell(1).setCellValue(_NAME);
        salaryRow.createCell(2).setCellValue(_CURRENT_SALARY);
        salaryRow.createCell(3).setCellValue(_CALCULATED_SALARY);
        salaryRow.createCell(4).setCellValue(_SALARY);
        salaryRow.createCell(5).setCellValue(_CURRENT_TAGS);
    }


    private static void fillEMARowTitle(Sheet sheet) {
        Row emaRow = sheet.createRow((short) 0);
        emaRow.createCell(0).setCellValue(_OPTA_PLAYER_ID);
        emaRow.createCell(1).setCellValue(_EMA_FP);
    }


    private static void fillSalaryRow(Row salaryRow, TemplateSoccerPlayer soccerPlayer) {
        salaryRow.createCell(0).setCellValue(soccerPlayer.optaPlayerId);
        salaryRow.createCell(1).setCellValue(soccerPlayer.name);
        salaryRow.createCell(2).setCellValue(soccerPlayer.salary);
        salaryRow.createCell(3).setCellValue(getSalary(calcEma(soccerPlayer.stats)));
        salaryRow.createCell(4).setCellFormula("D"+(salaryRow.getRowNum()+1));
        salaryRow.createCell(5).setCellValue(soccerPlayer.tags.toString().substring(1,soccerPlayer.tags.toString().length()-1));
    }


    private static void fillLogRow(HashMap<String, String> optaCompetitions, Row row, TemplateSoccerPlayer soccerPlayer,
                                   String teamName, SoccerPlayerStats stat) {
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


    private static void fillPivotRows(Row pivotRow, int statCounter, SoccerPlayerStats stat) {
        pivotRow.createCell((statCounter*2)+1).setCellValue(stat.fantasyPoints);
        pivotRow.createCell((statCounter*2)+2).setCellValue(stat.playedMinutes);
    }

    private static void fillPivotTitleRows(Row pivotTitleRow, int statNumber)  {
        for (int i=0; i<statNumber; i++) {
            pivotTitleRow.createCell((i*2)+1).setCellValue(_FANTASY_POINTS);
            pivotTitleRow.createCell((i*2)+2).setCellValue(_MINUTES_PLAYED);
        }
    }

    private static void fillLog() {

        Workbook wb = new XSSFWorkbook();

        XSSFSheet logSheet = (XSSFSheet) wb.createSheet(_LOG);
        XSSFSheet pivotSheet = (XSSFSheet) wb.createSheet(_PIVOT);
        XSSFSheet emaSheet = (XSSFSheet) wb.createSheet(_EMAS);
        XSSFSheet salarySheet = (XSSFSheet) wb.createSheet(_SALARIES);

        HashMap<String, String> optaCompetitions = new HashMap<>();
        for (OptaCompetition optaCompetition: OptaCompetition.findAll()) {
            optaCompetitions.put(optaCompetition.competitionId, optaCompetition.competitionName);
        }

        Row row, emaRow, pivotRow, salaryRow;

        fillLogRowTitle(logSheet);
        fillSalaryRowTitle(salarySheet);
        fillEMARowTitle(emaSheet);

        HashMap<String, String> soccerTeamsMap = new HashMap<>();

        int playerRowCounter = 1;
        int maxStatNumber = 0;
        int rowCounter = 1;

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

                fillSalaryRow(salaryRow, soccerPlayer);

                int statCounter = 0;
                for (SoccerPlayerStats stat : soccerPlayer.stats) {
                    row = logSheet.createRow((short)rowCounter++);
                    fillLogRow(optaCompetitions, row, soccerPlayer, teamName, stat);

                    fillPivotRows(pivotRow, statCounter, stat);

                    statCounter++;
                }

                maxStatNumber = (soccerPlayer.stats.size() > maxStatNumber)? soccerPlayer.stats.size(): maxStatNumber;
            }


        }
        Row pivotTitleRow = pivotSheet.createRow((short)0);
        pivotTitleRow.createCell(0).setCellValue(_OPTA_PLAYER_ID);
        fillPivotTitleRows(pivotTitleRow, maxStatNumber);

        try {
            FileOutputStream fileOut = new FileOutputStream(_FILENAME);
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
                if (!(stat.fantasyPoints == 0 && stat.playedMinutes < 5)) {
                    prevValue = stat.fantasyPoints;
                }
            }
            else {
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


    private static final String _OPTA_PLAYER_ID = "optaPlayerId";

    private static final String _NAME = "name";
    private static final String _POSITION = "position";
    private static final String _TEAM = "team";
    private static final String _COMPETITION = "competition";
    private static final String _DATE = "date";
    private static final String _TIME = "time";
    private static final String _MINUTES_PLAYED = "minutesPlayed";
    private static final String _FANTASY_POINTS = "fantasyPoints";

    private static final String _CURRENT_SALARY = "currentSalary";
    private static final String _CALCULATED_SALARY = "calculatedSalary";
    private static final String _SALARY = "salary";
    private static final String _CURRENT_TAGS = "currentTags";

    private static final String _EMA_FP = "EMAfp";

    private static final String _LOG = "Log";
    private static final String _PIVOT = "Pivot";
    private static final String _EMAS = "EMAs";
    private static final String _SALARIES = "Salaries";

    private static final String _FILENAME = "workbook.xlsx";
}
