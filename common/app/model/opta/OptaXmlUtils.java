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
        return getCreatedAtFromQuery("SELECT created_at FROM optaxml ORDER BY created_at ASC LIMIT 1;");
    }

    public static Date getLastDate() {
        return getCreatedAtFromQuery("SELECT created_at FROM optaxml ORDER BY created_at DESC LIMIT 1;");
    }

    private static Date getCreatedAtFromQuery(String query) {
        return DbSqlUtils.ExecutyQuery(query, new DbSqlUtils.IResultSetReader<Date>() {

            public Date handleResultSet(ResultSet resultSet) throws SQLException {
                return resultSet.getTimestamp("created_at");
            }

            public Date handleEmptyResultSet() { return new Date(0L); }
            public Date handleSQLException()   { throw new RuntimeException("WTF 1521"); }
        });
    }

    public static <T> T readNextXmlByDate(Date askedDate, DbSqlUtils.IResultSetReader<T> resultSetReader) {
        Timestamp last_date = new Timestamp(askedDate.getTime());
        String selectString = "SELECT * FROM optaxml WHERE created_at > '"+last_date+"' ORDER BY created_at LIMIT 1;";

        return DbSqlUtils.ExecutyQuery(selectString, resultSetReader);
    }

    public static int getRemainingXmlCount(Date askedDate) {
        Timestamp last_date = new Timestamp(askedDate.getTime());
        String selectString = "SELECT count(*) as remaining FROM optaxml WHERE created_at > '"+last_date+"';";

        return DbSqlUtils.ExecutyQuery(selectString, new DbSqlUtils.IResultSetReader<Integer>() {
            public Integer handleResultSet(ResultSet resultSet) throws SQLException {
                return resultSet.getInt("remaining");
            }

            public Integer handleEmptyResultSet() { return 0; }
            public Integer handleSQLException()   { throw new RuntimeException("WTF 1529"); }
        });
    }
}
