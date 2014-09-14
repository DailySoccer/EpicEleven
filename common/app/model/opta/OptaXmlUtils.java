package model.opta;

import play.Logger;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Date;


public class OptaXmlUtils {
    public static void insertXML(String xml, String headers, Date timestamp, String name, String feedType,
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

    public static Date getFirstDateFromOptaXML() {
        Date dateFirst = new Date(0L);

        try (Connection connection = play.db.DB.getConnection()) {
            String selectString = "SELECT created_at FROM optaxml ORDER BY created_at ASC LIMIT 1;";

            Statement stmt = connection.createStatement(ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY);
            ResultSet resultSet = stmt.executeQuery(selectString);

            if (resultSet != null && resultSet.next()) {
                dateFirst = resultSet.getTimestamp("created_at");
            }
        }
        catch (java.sql.SQLException e) {
            Logger.error("WTF 82847", e);
        }

        return dateFirst;
    }

    public static Date getLastDateFromOptaXML() {
        Date dateLast = new Date(0L);

        try (Connection connection = play.db.DB.getConnection()) {
            String selectString = "SELECT created_at FROM optaxml ORDER BY created_at DESC LIMIT 1;";

            Statement stmt = connection.createStatement(ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY);
            ResultSet resultSet = stmt.executeQuery(selectString);

            if (resultSet != null && resultSet.next()) {
                dateLast = resultSet.getTimestamp("created_at");
            }
        }
        catch (java.sql.SQLException e) {
            Logger.error("WTF 82848", e);
        }

        return dateLast;
    }
}
