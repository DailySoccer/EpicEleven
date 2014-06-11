package utils;

import com.fasterxml.jackson.databind.JsonNode;
import play.libs.Json;
import org.bson.types.ObjectId;
import java.util.List;
import java.util.ArrayList;
import java.util.Iterator;

public class ListUtils {
    public static List<String> stringListFromObjectIdList(List<ObjectId> objectIdList) {
        List<String> list = new ArrayList<>();
        for(ObjectId objectId: objectIdList) {
            list.add(objectId.toString());
        }
        return list;
    }

    public static List<String> stringListFromString(String regex, String params) {
        String[] strIds = params.split(regex);
        List<String> strIdsList = new ArrayList<>();
        for (String strId: strIds)
            strIdsList.add(strId);
        return strIdsList;
    }

    public static <T> List<T> listFromIterator(Iterator<T> iter) {
        List<T> copy = new ArrayList<T>();
        while (iter.hasNext())
            copy.add(iter.next());
        return copy;
    }

    public static List<ObjectId> objectIdListFromJson(String jsonData) {
        List<ObjectId> idsList = new ArrayList<>();

        JsonNode jsonNode = Json.parse(jsonData);
        assert (jsonNode.isArray());

        Iterator<JsonNode> iter = jsonNode.iterator();
        while (iter.hasNext()) {
            String strObjectId = iter.next().asText();
            idsList.add(new ObjectId(strObjectId));
        }

        return idsList;
    }
}
