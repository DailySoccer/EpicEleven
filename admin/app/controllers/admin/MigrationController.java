package controllers.admin;

import play.Logger;
import play.db.DB;
import play.mvc.Controller;
import play.mvc.Result;

import java.io.UnsupportedEncodingException;
import java.sql.*;


/**
 * Created by gnufede on 31/07/14.
 */
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

    public static String translate(String requestBody) {
        try {
            return new String (new String (new String (requestBody.getBytes("ISO-8859-1"), "UTF-8").
                                    getBytes("ISO-8859-1"), "UTF-8").
                                    getBytes("ISO-8859-1"), "UTF-8");

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
                    _optaResultSet = _stmt.executeQuery("SELECT id, xml FROM optaxml "+
                            " ORDER BY created_at LIMIT " +
                            RESULTS_PER_QUERY + " OFFSET " + _nextDocToParseIndex + ";");
                }

                if (_optaResultSet.next()) {
                    _nextDocToParseIndex += 1;

                    String bodyText = translate(_optaResultSet.getString("xml"));
                    int documentId = _optaResultSet.getInt("id");

                    updateXML(documentId, bodyText);
                } else {
                    _isFinished = true;
                }
            } catch (SQLException e) {
                Logger.error("WTF 12421", e);
            }
        }
        closeConnection();
    }

    public static void updateXML(int documentId, String xml) {

        String updateString = "UPDATE optaxml SET xml = ? WHERE id = ?";

        try (PreparedStatement stmt = _connection.prepareStatement(updateString)) {
            stmt.setString(1, xml);
            stmt.setInt(2, documentId);

            if (!stmt.execute()) {
                Logger.error("Unsuccessful update in OptaXML");
            }
        }
        catch (java.sql.SQLException e) {
            Logger.error("WTF 56312: ", e);
        }
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
