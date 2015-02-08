package utils;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import play.Logger;

public class JsonUtils {

    public static <T> T fromJSON(final String json, final TypeReference<T> type) {
        T ret = null;

        try {
            ret = new ObjectMapper().readValue(json, type);
        }
        catch (Exception exc) {
            Logger.error("WTF 2229", exc);
        }

        return ret;
    }


    // Cuando el servidor nos devuelve una String, lo hace envuelta en comillas, tenemos que quitarlas
    public static String extractStringValue(final JsonNode jsonNode, final String key) {

        String ret = null;
        JsonNode val = jsonNode.findValue(key);

        if (val != null) {
            ret = val.toString().replace("\"", ""); // TODO: Solo la primera y la final
        }

        return ret;
    }
}
