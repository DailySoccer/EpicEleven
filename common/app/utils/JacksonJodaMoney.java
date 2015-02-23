package utils;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import model.Product;
import org.joda.money.CurrencyUnit;
import org.joda.money.Money;

import java.io.IOException;

public class JacksonJodaMoney {
    public static class MoneySerializer extends JsonSerializer<Money> {
        @Override
        public void serialize(Money value, JsonGenerator jsonGenerator, SerializerProvider serializerProvider) throws IOException {
            jsonGenerator.writeString(value.toString());
        }
    }

    public static class MoneyDeserializer extends JsonDeserializer<Money> {
        @Override
        public Money deserialize(JsonParser jsonParser, DeserializationContext deserializationContext) throws IOException {
            String value = jsonParser.readValueAs(String.class);
            try {
                return Money.parse(value);
            }
            catch (IllegalArgumentException e) {
                // Logger.warn("MoneyDeserializer: Versión antigua expresando el dinero como \"Int32\" o \"Double\"");
                return MoneyUtils.of(Double.parseDouble(value));
            }
        }
    }
}
