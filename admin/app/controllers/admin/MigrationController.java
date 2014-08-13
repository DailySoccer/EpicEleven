package controllers.admin;

import model.Model;
import play.Logger;
import play.db.DB;
import play.mvc.Controller;
import play.mvc.Result;

import java.io.UnsupportedEncodingException;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;


public class MigrationController extends Controller {
    /*
    STEPS:
    1- Create table newoptaxml
    2- Loop over newoptaxml, for each document found:
        2.1- Read the document
        2.2- Translate the body to correct encoding
        2.3- Insert the document inside newoptaxml
    3- Delete table optaxml: "DROP TABLE optaxml;"
    4- Rename newoptaxml to optaxml: "ALTER TABLE distributors RENAME TO suppliers;"
     */

    public static Result migrate() {
        runMigration();
        return ok("Migration finished");
    }

    public static String translate(String requestBody, String feedType) {
        if (!requestBody.contains("Ã")) {
            return requestBody;
        }
        /*
        if (!(feedType.equals("F40") || feedType.equals("F9")))
            return requestBody;
        */
        try {
            String translated = requestBody;
            String temp = requestBody;

            int times = 0;
            while (!(temp.indexOf('ô')>0 || temp.indexOf('á')>0 || temp.indexOf('é')>0) && times<7) {
                if (temp.indexOf("voire")>0)
                    Logger.debug(temp.substring((temp.indexOf("voire")-14), (temp.indexOf("voire")+5)));
                times++;
                translated = temp;
                temp = new String(temp.getBytes("ISO-8859-1"), "UTF-8");
            }
            Logger.debug("Translated times: {}", times);
            if (times < 7)
                return temp;
            return requestBody;

            /*
            if (temp.indexOf(symbol) >= 0) {
                return null;
            }
            */

        } catch (UnsupportedEncodingException e) {
            Logger.error("WTF 6534", e);
        }
        return requestBody;
    }

    public static void runMigration() {
        if (_connection == null) {
            createConnection();
        }


        while (!_isFinished) {
            try {
                if (_nextDocToParseIndex % RESULTS_PER_QUERY == 0 || _optaResultSet == null) {
                    if (_stmt != null) {
                        _stmt.close();
                        _stmt = null;
                    }

                    _stmt = _connection.createStatement(ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY);
                    _optaResultSet = _stmt.executeQuery("SELECT id, xml, feed_type FROM optaxml "+
                            " ORDER BY created_at LIMIT " +
                            RESULTS_PER_QUERY + " OFFSET " + _nextDocToParseIndex + ";");
                }

                if (_optaResultSet.next()) {
                    _nextDocToParseIndex += 1;

                    String feedType = _optaResultSet.getString("feed_type");
                    String bodyText = translate(_optaResultSet.getString("xml"), feedType);
                    int documentId = _optaResultSet.getInt("id");

                    if (bodyText != null) {
                        Model.updateXML(documentId, bodyText);
                    }
                    else {
                        Model.deleteXML(documentId);
                    }
                } else {
                    _isFinished = true;
                }
            } catch (SQLException e) {
                Logger.error("WTF 12421", e);
            }
        }
        closeConnection();
    }


    private static void createConnection() {
        _connection = DB.getConnection();
        try {
            _connection.setAutoCommit(false);
        } catch (SQLException e) {
            Logger.error("WTF 14991", e);
        }
    }

    private static void closeConnection() {
        try {
            if (_stmt != null) {
                _stmt.close();

                _stmt = null;
                _optaResultSet = null;
            }
            _connection.close();
            _connection = null;
    }
    catch (SQLException e) {
            Logger.error("WTF 2442 SQLException: ", e);
        }
    }

    private static Connection _connection;
    private static Statement _stmt;
    private static ResultSet _optaResultSet;
    private static int RESULTS_PER_QUERY = 500;
    private static int _nextDocToParseIndex = 0;
    private static boolean _isFinished = false;
}
