package controllers.admin;


import com.mongodb.BasicDBObject;
import com.mongodb.DBObject;
import model.*;
import model.opta.OptaCompetition;
import org.apache.poi.openxml4j.exceptions.InvalidFormatException;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellReference;
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

        response().setHeader("Content-Disposition", "attachment; filename=ActivityLog.xlsx");

        File tempFile = fillLog();
        FileInputStream activityLogStream = new FileInputStream(tempFile);
        assert tempFile.delete();
        return ok(activityLogStream);
    }


    public static Result upload() {
        Http.MultipartFormData body = request().body().asMultipartFormData();
        Http.MultipartFormData.FilePart newSalariesFile = body.getFile("excel");
        if (newSalariesFile != null) {
            FlashMessage.success(parseSalariesFile(newSalariesFile.getFile()) +" Salaries read successfully");
            return redirect(routes.ExcelController.index());
        } else {
            FlashMessage.danger("Missing file, select one through \"Choose file\"");
            return redirect(routes.ExcelController.index());
        }
    }


    private static int parseSalariesFile(File file) {
        int salariesRead = 0;

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

            FormulaEvaluator evaluator = wb.getCreationHelper().createFormulaEvaluator();

            // Empezamos desde el 1 para saltarnos la fila título
            for (int i = 1; i<= sheet.getLastRowNum(); i++) {
                myRow = sheet.getRow(i);

                newSalaries.put(myRow.getCell(_SalarySheet.OPTA_PLAYER_ID.column).getStringCellValue(),
                                (int)evaluator.evaluate(myRow.getCell(_SalarySheet.SALARY.column)).getNumberValue());

                if (myRow.getCell(_SalarySheet.CURRENT_TAGS.column)==null) {
                    newTags.put(myRow.getCell(_SalarySheet.OPTA_PLAYER_ID.column).getStringCellValue(), "");
                } else {
                    newTags.put(myRow.getCell(_SalarySheet.OPTA_PLAYER_ID.column).getStringCellValue(),
                            myRow.getCell(_SalarySheet.CURRENT_TAGS.column).getStringCellValue().trim());
                }
            }

            BatchWriteOperation batchWriteOperation = new BatchWriteOperation(Model.templateSoccerPlayers().getDBCollection().initializeUnorderedBulkOperation());

            for (String optaPlayerId : newSalaries.keySet()) {

                if (!TemplateSoccerPlayer.exists(optaPlayerId)) {
                    Logger.error("Se ha detectado un futbolista {} en el excel que no existe en la base de datos", optaPlayerId);
                    continue;
                }

                DBObject query = new BasicDBObject(_OPTA_PLAYER_ID, optaPlayerId);
                BasicDBObject bdo = new BasicDBObject(_SALARY, newSalaries.get(optaPlayerId));

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
            salariesRead = newSalaries.size();

        }
        catch (NullPointerException e) {
            Logger.error("WTF 1252: No hay hoja Salaries");
        }
        return salariesRead;
    }


    private static void autoSizeColumns(Sheet sheet, int numCols) {
        for (int i=0; i<=numCols; i++) {
            sheet.autoSizeColumn(i);
        }
    }


    private static void fillLogRowTitle(Sheet sheet) {
        Row rowTitle = sheet.createRow((short)0);

        for (_LogSheet logValue : _LogSheet.values()) {
            rowTitle.createCell(logValue.column).setCellValue(logValue.colName);
        }

    }


    private static void fillSalaryRowTitle(Sheet sheet) {
        Row salaryRow = sheet.createRow((short)0);

        for (_SalarySheet salaryValue: _SalarySheet.values()) {
            salaryRow.createCell(salaryValue.column).setCellValue(salaryValue.colName);
        }
    }


    private static void fillEMARowTitle(Sheet sheet) {
        Row emaRow = sheet.createRow((short) 0);
        for (_EMASheet emaValue: _EMASheet.values()) {
            emaRow.createCell(emaValue.column).setCellValue(emaValue.colName);
        }
    }


    private static void fillSalaryRow(Row salaryRow, TemplateSoccerPlayer soccerPlayer) {

        salaryRow.createCell(_SalarySheet.OPTA_PLAYER_ID.column).setCellValue(soccerPlayer.optaPlayerId);
        salaryRow.createCell(_SalarySheet.NAME.column).setCellValue(soccerPlayer.name);
        salaryRow.createCell(_SalarySheet.CURRENT_SALARY.column).setCellValue(soccerPlayer.salary);

        Cell calculatedSalaryCell = salaryRow.createCell(_SalarySheet.CALCULATED_SALARY.column);
        calculatedSalaryCell.setCellValue(getSalary(calcEma(soccerPlayer.stats)));
        salaryRow.createCell(_SalarySheet.SALARY.column).setCellFormula(new CellReference(calculatedSalaryCell).formatAsString());

        salaryRow.createCell(_SalarySheet.CURRENT_TAGS.column).setCellValue(soccerPlayer.tags.toString().substring(1, soccerPlayer.tags.toString().length() - 1));

        TemplateSoccerTeam team = TemplateSoccerTeam.findOne(soccerPlayer.templateTeamId);
        salaryRow.createCell(_SalarySheet.TEAM.column).setCellValue(team != null ? team.name : "unknown");
    }


    private static void fillLogRow(HashMap<String, String> optaCompetitions, Row row, TemplateSoccerPlayer soccerPlayer,
                                   String teamName, SoccerPlayerStats stat) {
        row.createCell(_LogSheet.OPTA_PLAYER_ID.column).setCellValue(soccerPlayer.optaPlayerId);
        row.createCell(_LogSheet.NAME.column).setCellValue(soccerPlayer.name);
        row.createCell(_LogSheet.POSITION.column).setCellValue(soccerPlayer.fieldPos.name());
        row.createCell(_LogSheet.TEAM.column).setCellValue(teamName);
        row.createCell(_LogSheet.COMPETITION.column).setCellValue(optaCompetitions.get(stat.optaCompetitionId));
        row.createCell(_LogSheet.DATE.column).setCellValue(new DateTime(stat.startDate).toString(DateTimeFormat.forPattern("dd/MM/yyyy")));
        row.createCell(_LogSheet.TIME.column).setCellValue(new DateTime(stat.startDate).toString(DateTimeFormat.forPattern("HH:mm")));
        row.createCell(_LogSheet.MINUTES_PLAYED.column).setCellValue(stat.playedMinutes);
        row.createCell(_LogSheet.FANTASY_POINTS.column).setCellValue(stat.fantasyPoints);
    }


    private static void fillPivotRows(Row pivotRow, int statCounter, SoccerPlayerStats stat) {
        pivotRow.createCell((statCounter*2)+1).setCellValue(stat.fantasyPoints);
        pivotRow.createCell((statCounter*2)+2).setCellValue(stat.playedMinutes);
    }

    private static void fillPivotTitleRows(Row pivotTitleRow, int statNumber, Sheet sheet)  {
        for (int i=0; i<((statNumber*2)+1); i++) {
            pivotTitleRow.createCell(i).setCellValue(_PivotSheet.getName(i));
        }
    }

    private static File fillLog() {

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

                pivotRow = pivotSheet.createRow(playerRowCounter);
                salaryRow = salarySheet.createRow(playerRowCounter);
                emaRow = emaSheet.createRow(playerRowCounter++);

                pivotRow.createCell(_PivotSheet.OPTA_PLAYER_ID.column).setCellValue(soccerPlayer.optaPlayerId);

                emaRow.createCell(_EMASheet.OPTA_PLAYER_ID.column).setCellValue(soccerPlayer.optaPlayerId);
                emaRow.createCell(_EMASheet.EMA_FP.column).setCellValue(calcEma(soccerPlayer.stats));

                fillSalaryRow(salaryRow, soccerPlayer);

                int statCounter = 0;
                for (SoccerPlayerStats stat : soccerPlayer.stats) {
                    row = logSheet.createRow(rowCounter++);
                    fillLogRow(optaCompetitions, row, soccerPlayer, teamName, stat);

                    fillPivotRows(pivotRow, statCounter, stat);

                    statCounter++;
                }

                maxStatNumber = (soccerPlayer.stats.size() > maxStatNumber)? soccerPlayer.stats.size(): maxStatNumber;
            }


        }
        Row pivotTitleRow = pivotSheet.createRow((short)0);
        pivotTitleRow.createCell(_PivotSheet.OPTA_PLAYER_ID.column).setCellValue(_PivotSheet.OPTA_PLAYER_ID.colName);
        fillPivotTitleRows(pivotTitleRow, maxStatNumber, pivotSheet);

        autoSizeColumns(pivotSheet, (maxStatNumber*2)+1);
        autoSizeColumns(logSheet, _LogSheet.lastCol.column);
        autoSizeColumns(salarySheet, _SalarySheet.lastCol.column);
        autoSizeColumns(emaSheet, _EMASheet.lastCol.column);

        try {
            File tempFile = File.createTempFile("temp-ActivityLog", ".xlsx");
            FileOutputStream fileOut = new FileOutputStream(tempFile);
            wb.write(fileOut);
            fileOut.close();

            return tempFile;
        }
        catch (FileNotFoundException e) {
            Logger.error("WTF 23126");
        }
        catch (IOException e) {
            Logger.error("WTF 21276");
        }
        return null; //Si ha ocurrido excepción devolvemos null
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

    private static final String _MINUTES_PLAYED = "minutesPlayed";
    private static final String _FANTASY_POINTS = "fantasyPoints";

    private static final String _NAME = "name";
    private static final String _POSITION = "position";
    private static final String _TEAM = "team";
    private static final String _COMPETITION = "competition";
    private static final String _DATE = "date";
    private static final String _TIME = "time";

    private static final String _CURRENT_SALARY = "currentSalary";
    private static final String _CALCULATED_SALARY = "calculatedSalary";
    private static final String _SALARY = "salary";
    private static final String _CURRENT_TAGS = "currentTags";

    private static final String _EMA_FP = "EMAfp";

    private static final String _LOG = "Log";
    private static final String _PIVOT = "Pivot";
    private static final String _EMAS = "EMAs";
    private static final String _SALARIES = "Salaries";


    private enum _LogSheet {
        OPTA_PLAYER_ID  (0, _OPTA_PLAYER_ID),
        NAME            (1, _NAME),
        POSITION        (2, _POSITION),
        TEAM            (3, _TEAM),
        COMPETITION     (4, _COMPETITION),
        DATE            (5, _DATE),
        TIME            (6, _TIME),
        MINUTES_PLAYED  (7, _MINUTES_PLAYED),
        FANTASY_POINTS  (8, _FANTASY_POINTS);

        public final int column;
        public final String colName;

        public static final _LogSheet lastCol = FANTASY_POINTS;

        _LogSheet(int c, String name) {
            column = c;
            colName = name;
        }
    }

    private enum _SalarySheet {
        OPTA_PLAYER_ID      (0, _OPTA_PLAYER_ID),
        NAME                (1, _NAME),
        CURRENT_SALARY      (2, _CURRENT_SALARY),
        CALCULATED_SALARY   (3, _CALCULATED_SALARY),
        SALARY              (4, _SALARY),
        CURRENT_TAGS        (5, _CURRENT_TAGS),
        TEAM                (6, _TEAM);

        public final int column;
        public final String colName;

        public static final _SalarySheet lastCol = TEAM;

        _SalarySheet(int c, String name) {
            column = c;
            colName = name;
        }
    }

    private enum _EMASheet {
        OPTA_PLAYER_ID  (0, _OPTA_PLAYER_ID),
        EMA_FP          (1, _EMA_FP);

        public final int column;
        public final String colName;

        public static final _EMASheet lastCol = EMA_FP;

        _EMASheet(int c, String name) {
            column = c;
            colName = name;
        }
    }

    private enum _PivotSheet {
        OPTA_PLAYER_ID  (0, _OPTA_PLAYER_ID);

        public final int column;
        public final String colName;

        _PivotSheet(int c, String name) {
            column = c;
            colName = name;
        }

        public static String getName(int col) {
            return (col==0)? _PivotSheet.OPTA_PLAYER_ID.colName:
                    (col%2==0)? _MINUTES_PLAYED: _FANTASY_POINTS;

        }
    }

}
