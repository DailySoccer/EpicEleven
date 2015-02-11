package utils;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import org.joda.money.Money;
import play.Logger;

public class JsonUtils {

    public static <T> T fromJSON(final String json, final TypeReference<T> type) {
        T ret = null;

        try {
            ret = getObjectMapper().readValue(json, type);
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

    static ObjectMapper getObjectMapper() {
        if (objectMapper == null) {
            SimpleModule module = new SimpleModule();
            module.addDeserializer(Money.class, new JacksonJodaMoney.MoneyDeserializer());
            module.addSerializer(Money.class, new JacksonJodaMoney.MoneySerializer());

            objectMapper = new ObjectMapper();
            objectMapper.registerModule(module);
        }
        return objectMapper;
    }

    static ObjectMapper objectMapper;
}
