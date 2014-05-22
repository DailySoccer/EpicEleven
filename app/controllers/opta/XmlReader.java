package controllers.opta;

import model.Model;

import java.util.*;

import org.json.XML;
import org.json.JSONObject;
/**
 * Created by gnufede on 20/05/14.
 */
public class XmlReader {
    public static int PRETTY_PRINT_INDENT_FACTOR = 4;
    private String body;

    private void execute(String xmlString) throws Exception {
        JSONObject jsonObject = XML.toJSONObject(xmlString);
        Date timestamp = new Date();
        jsonObject = jsonObject.put("timestamp", timestamp.getTime());
        String jsonPrettyPrintString = jsonObject.toString(PRETTY_PRINT_INDENT_FACTOR);

        Model.optaDB().insert(jsonPrettyPrintString);

    }

    public XmlReader (String body){
        this.body = body;
        try {
            this.execute(body.substring(body.indexOf('<')));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }



}
