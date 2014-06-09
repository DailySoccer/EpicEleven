package utils;

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
}
