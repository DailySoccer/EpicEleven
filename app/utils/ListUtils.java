package utils;

import com.fasterxml.jackson.databind.JsonNode;
import play.libs.Json;
import org.bson.types.ObjectId;
import java.util.List;
import java.util.ArrayList;
import java.util.Iterator;

public class ListUtils {
    /**
     * Convertir una lista de ObjectId en su equivalente en formato String
     * @param objectIdList Lista de ObjectId (mongoDB)
     * @return Lista de String (identificadores de ObjectId en formato String)
     */
    public static List<String> stringListFromObjectIdList(List<ObjectId> objectIdList) {
        List<String> list = new ArrayList<>();
        for(ObjectId objectId: objectIdList) {
            list.add(objectId.toString());
        }
        return list;
    }

    /**
     * Extraer los campos de una cadena (proporcionada como CSV)
     * @param regex Caracteres usados para separar campos
     * @param params Cadena con formato tipo CSV (comma-separated values) ej. "valor1,valor2,valor3"
     * @return La lista de campos ej. [valor1, valor2, valor3]
     */
    public static List<String> listFromCSV(String regex, String params) {
        List<String> strIdsList = new ArrayList<>();
        for (String strId: params.split(regex))
            strIdsList.add(strId);
        return strIdsList;
    }

    /**
     * Convertir un iterator a una lista  (Iterator -> List>
     * @param iter Iterator
     * @param <T> Tipo del iterator
     * @return La lista de elementos (extraidos del iterator)
     */
    public static <T> List<T> listFromIterator(Iterator<T> iter) {
        List<T> list = new ArrayList<T>();
        while (iter.hasNext())
            list.add(iter.next());
        return list;
    }

    /**
     * Extraer los campos de un jsonArray
     * @param jsonData Array de campos en formato json  ej. "[valor1, valor2, valor3]"
     * @return La lista de ObjectIds ej. [valor1, valor2, valor3]
     */
    public static List<ObjectId> objectIdListFromJson(String jsonData) {
        List<ObjectId> idsList = new ArrayList<>();

        JsonNode jsonNode = Json.parse(jsonData);
        assert (jsonNode.isArray());

        Iterator<JsonNode> iter = jsonNode.iterator();
        while (iter.hasNext())
            idsList.add(new ObjectId(iter.next().asText()));

        return idsList;
    }

    /**
     * Extraer los campos de un jsonArray
     * @param jsonData Array de campos en formato json  ej. "[valor1, valor2, valor3]"
     * @return La lista de cadenas ej. [valor1, valor2, valor3]
     */
    public static List<String> stringListFromJson(String jsonData) {
        List<String> list = new ArrayList<>();

        JsonNode jsonNode = Json.parse(jsonData);
        assert (jsonNode.isArray());

        Iterator<JsonNode> iter = jsonNode.iterator();
        while (iter.hasNext())
            list.add(iter.next().asText());

        return list;
    }
}
