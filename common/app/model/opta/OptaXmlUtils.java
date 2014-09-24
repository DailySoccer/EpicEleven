package model.opta;

import play.Logger;
import utils.DbSqlUtils;

import java.sql.*;
import java.util.Date;


public class OptaXmlUtils {

    public static void insertXml(String xml, String headers, Date timestamp, String name, String feedType,
                                 String gameId, String competitionId, String seasonId, Date lastUpdated) {

        String insertString = "INSERT INTO optaxml (xml, headers, created_at, name, feed_type, game_id, competition_id," +
                              "season_id, last_updated) VALUES (?,?,?,?,?,?,?,?,?)";

        try (Connection connection = play.db.DB.getConnection()) {
            try (PreparedStatement stmt = connection.prepareStatement(insertString)) {
                stmt.setString(1, xml);
                stmt.setString(2, headers);
                stmt.setTimestamp(3, new java.sql.Timestamp(timestamp.getTime()));
                stmt.setString(4, name);
                stmt.setString(5, feedType);
                stmt.setString(6, gameId);
                stmt.setString(7, competitionId);
                stmt.setString(8, seasonId);
                stmt.setTimestamp(9, new java.sql.Timestamp(lastUpdated.getTime()));

                stmt.execute();

                if (stmt.getUpdateCount() == 1) {
                    Logger.info("Successful insert of {}", name);
                }
                else {
                    Logger.error("WTF 1906, no se inserto el fichero de opta {}", name);
                }
            }
        }
        catch (java.sql.SQLException e) {
            Logger.error("WTF 5039", e);
        }
    }

    public static Date getFirstDate() {
        return getCreatedAt("SELECT created_at FROM optaxml ORDER BY created_at ASC LIMIT 1;");
    }

    public static Date getLastDate() {
        return getCreatedAt("SELECT created_at FROM optaxml ORDER BY created_at DESC LIMIT 1;");
    }

    private static Date getCreatedAt(String query) {
        return DbSqlUtils.ExecutyQuery(query, new DbSqlUtils.IResultSetReader<Date>() {

            public Date handleResultSet(ResultSet resultSet) throws SQLException {
                return resultSet.next() ? resultSet.getTimestamp("created_at") : new Date(0L);
            }

            public Date handleSQLException() {
                return new Date(0L);
            }
        });
    }

    public static ResultSet getNextXmlByDate(Statement stmt, Date askedDate) throws SQLException {
        Timestamp last_date = new Timestamp(askedDate.getTime());
        String selectString = "SELECT * FROM optaxml WHERE created_at > '"+last_date+"' ORDER BY created_at LIMIT 1;";
        return stmt.executeQuery(selectString);
    }

    public static ResultSet getRemainingXmlCount(Statement stmt, Date askedDate) throws SQLException {
        Timestamp last_date = new Timestamp(askedDate.getTime());
        String selectString = "SELECT count(*) as remaining FROM optaxml WHERE created_at > '"+last_date+"';";
        return stmt.executeQuery(selectString);
    }
}
