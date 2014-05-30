package model;


import com.mongodb.DBObject;
import java.util.Map;

/**
 * Created by gnufede on 30/05/14.
 */
public class OptaDB {
    public String xml;
    public String name;
    public DBObject json;
    public Map<String, String[]> headers;
    public long startDate;
    public long endDate;

    public OptaDB(String xml, DBObject json, String name, Map<String, String[]> headers, long startDate, long endDate) {
        this.xml = xml;
        this.json = json;
        this.name = name;
        this.headers = headers;
        this.startDate = startDate;
        this.endDate = endDate;
    }

    public OptaDB() {}
}
