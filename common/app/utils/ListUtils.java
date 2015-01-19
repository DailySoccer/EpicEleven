package utils;

import com.fasterxml.jackson.databind.JsonNode;
import model.JongoId;
import org.bson.types.ObjectId;
import play.libs.Json;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

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
     * Convertir un iterator a una lista  (Iterator -> List>
     * @param iter Iterator
     * @param <T> Tipo del iterator
     * @return La lista de elementos (extraidos del iterator)
     */
    public static <T> List<T> asList(Iterator<T> iter) {
        List<T> list = new ArrayList<>();
        if (iter != null) {
            while (iter.hasNext())
                list.add(iter.next());
        }
        return list;
    }

    public static <T> List<T> asList(Iterable<T> iterable) {
        if (iterable instanceof List) {
            return (List<T>) iterable;
        }
        return asList(iterable.iterator());
    }

    /**
     * Extraer los campos de un jsonArray
     * @param jsonData Array de campos en formato json  ej. "[valor1, valor2, valor3]"
     * @return La lista de ObjectIds ej. [valor1, valor2, valor3]
     */
    public static List<ObjectId> objectIdListFromJson(String jsonData) {
        List<ObjectId> idsList = new ArrayList<>();

        try {
            JsonNode jsonNode = Json.parse(jsonData);
            if (!jsonNode.isArray()) {
                play.Logger.error("WTF 1285: !jsonNode.isArray()");
            }

            for (JsonNode aJsonNode : jsonNode) {
                idsList.add(new ObjectId(aJsonNode.asText()));
            }
        } catch (RuntimeException e) {
            play.Logger.error("WTF 1286: objectIdListFromJson invalid");
        }

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

        for (JsonNode aJsonNode : jsonNode) {
            list.add(aJsonNode.asText());
        }

        return list;
    }

    public static <T extends JongoId> List<ObjectId> convertToIdList(List<T> listOfPojos) {
        List<ObjectId> ret = new ArrayList<>(listOfPojos.size());

        for (T pojo: listOfPojos)
            ret.add(pojo.getId());

        return ret;
    }

    /**
     * Obtener una lista aleatoria de elementos
     * @param list  Lista de la que se obtienen los elementos
     * @param numElements Numero de elementos
     * @return Lista aleatoria
     */
    public static <T> List<T> randomElements(List<T> list, int numElements) {
        List<T> result = new ArrayList<>();

        // Indices al array original (que iremos eliminando para no repetir los elementos)
        List<Integer> indexes = new ArrayList<>(list.size());
        for (int i=0; i<list.size(); i++)
            indexes.add(i);

        // Elegir x elementos aleatorios
        Random rand = new Random();
        while (numElements > 0 && indexes.size() > 0) {
            int index = rand.nextInt(indexes.size());

            int elemIdx = indexes.get(index);
            result.add(list.get(elemIdx));

            indexes.remove(index);
            numElements--;
        }

        return result;
    }
}
