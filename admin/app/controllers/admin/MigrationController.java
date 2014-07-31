package controllers.admin;

import play.Logger;
import play.db.DB;
import play.mvc.Controller;
import play.mvc.Result;

import java.io.UnsupportedEncodingException;
import java.sql.*;
import java.util.Date;


/**
 * Created by gnufede on 31/07/14.
 */
public class MigrationController extends Controller {
    /*
    STEPS:
    1- Create table optaxml2
    2- Loop over optaxml2, for each document found:
        2.1- Read the document
        2.2- Translate the body to correct encoding
        2.3- Insert the document inside optaxml2
    3- Delete table optaxml: "DROP TABLE optaxml;"
    4- Rename optaxml2 to optaxml: "ALTER TABLE distributors RENAME TO suppliers;"
     */

    public static Result migrate() {
        MigrationController mg = new MigrationController();
        mg.runMigration();
        return ok("Migration finished");
    }

    public String translate(String requestBody) {
        try {
            return new String (new String (new String (requestBody.getBytes("ISO-8859-1"), "UTF-8").
                                    getBytes("ISO-8859-1"), "UTF-8").
                                    getBytes("ISO-8859-1"), "UTF-8");

        } catch (UnsupportedEncodingException e) {
            Logger.error("WTF 6534", e);
        }
        return requestBody;
    }

    public void runMigration() {
        if (_connection == null) {
            createConnection();
        }

        try {
            _stmt = _connection.createStatement(ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY);
            _optaResultSet = _stmt.executeQuery("SELECT * FROM optaxml "+
                                                " ORDER BY created_at LIMIT " +
                    RESULTS_PER_QUERY + " OFFSET " + _nextDocToParseIndex + ";");

            _nextDocToParseIndex += 1;

            if (_optaResultSet.next()) {
                String bodyText = translate(_optaResultSet.getString("xml"));
                String headers = _optaResultSet.getString("headers");
                String feedType = _optaResultSet.getString("feed_type");
                String name = _optaResultSet.getString("name");
                String gameId = _optaResultSet.getString("game_id");
                String competitionId = _optaResultSet.getString("competition_id");
                String seasonId = _optaResultSet.getString("season_id");
                Date lastUpdated = _optaResultSet.getTimestamp("last_updated");
                Date createdAt = _optaResultSet.getTimestamp("created_at");

                insertRightXML(bodyText, headers, createdAt, name, feedType, gameId,
                                    competitionId, seasonId, lastUpdated);
            }
        } catch (SQLException e) {
            Logger.error("WTF 12421", e);
        }
        closeConnection();
    }

    private void ensurePostgresDB() throws SQLException {
        try (Statement stmt = _connection.createStatement()) {
            stmt.execute("CREATE TABLE IF NOT EXISTS optaxml2 (" +
                    " id serial PRIMARY KEY, " +
                    " xml text, " +
                    " headers text, " +
                    " created_at timestamp, " +
                    " name text, " +
                    " feed_type text, " +
                    " game_id text, " +
                    " competition_id text, " +
                    " season_id text, " +
                    " last_updated timestamp " +
                    " );");

            // http://dba.stackexchange.com/questions/35616/create-index-if-it-does-not-exist
            stmt.execute("DO $$ " +
                    "BEGIN " +
                    "IF NOT EXISTS ( " +
                    "SELECT 1 " +
                    "FROM  pg_class c " +
                    "JOIN  pg_namespace n ON n.oid = c.relnamespace " +
                    "WHERE c.relname = 'created_at_index' " +
                    "AND   n.nspname = 'public' " +
                    ") THEN " +
                    "CREATE INDEX created_at_index ON public.optaxml2 (created_at); " +
                    "END IF; " +
                    "END$$;");
        }
    }


    public  void insertRightXML(String xml, String headers, Date timestamp, String name, String feedType,
                                 String gameId, String competitionId, String seasonId, Date lastUpdated) {

        String insertString = "INSERT INTO optaxml2 (xml, headers, created_at, name, feed_type, game_id, competition_id," +
                "season_id, last_updated) VALUES (?,?,?,?,?,?,?,?,?)";

        try (PreparedStatement stmt = _connection.prepareStatement(insertString)) {
            stmt.setString(1, xml);
            stmt.setString(2, headers);
            if (timestamp != null) {
                stmt.setTimestamp(3, new java.sql.Timestamp(timestamp.getTime()));
            } else {
                stmt.setTimestamp(3, null);
            }
            stmt.setString(4, name);
            stmt.setString(5, feedType);
            stmt.setString(6, gameId);
            stmt.setString(7, competitionId);
            stmt.setString(8, seasonId);

            if (lastUpdated != null) {
                stmt.setTimestamp(9, new java.sql.Timestamp(lastUpdated.getTime()));
            } else {
                stmt.setTimestamp(9, null);
            }

            if (!stmt.execute()) {
                Logger.error("Unsuccessful insert in OptaXML2");
            }
        }
        catch (java.sql.SQLException e) {
            Logger.error("WTF 56312: ", e);
        }
    }


    private void createConnection() {
        _connection = DB.getConnection();
        try {
            _connection.setAutoCommit(false);
        } catch (SQLException e) {
            Logger.error("WTF 14991", e);
        }
    }

    private void closeConnection() {
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

    private Connection _connection;
    private Statement _stmt;
    private ResultSet _optaResultSet;
    private int RESULTS_PER_QUERY = 500;
    private int _nextDocToParseIndex = 0;
}
