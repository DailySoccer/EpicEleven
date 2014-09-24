package utils;

import play.Logger;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

public class DbSqlUtils {
    static public <T> T ExecutyQuery(String query, IResultSetReader<T> reader) {
        try (Connection connection = play.db.DB.getConnection()) {
            try (Statement stmt = connection.createStatement(ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY)) {
                try (ResultSet resultSet = stmt.executeQuery(query)) {
                    return reader.handleResultSet(resultSet);
                }
            }
        }
        catch (java.sql.SQLException e) {
            Logger.error("WTF 82847, query: " + query, e);
            return reader.handleSQLException();
        }
    }

    public interface IResultSetReader<T> {
        T handleResultSet(ResultSet resultSet) throws SQLException;
        T handleSQLException();
    }
}
