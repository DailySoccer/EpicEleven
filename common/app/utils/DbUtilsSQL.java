package utils;

import java.sql.ResultSet;

public class DbUtilsSQL {
    static public void ExecutyQuery(String query, IResultSetReader reader) {


    }

    public interface IResultSetReader {
        void onResultSet(ResultSet resultSet);
    }
}
