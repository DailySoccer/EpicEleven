package utils;

import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.databind.*;
import org.joda.money.CurrencyUnit;
import org.joda.money.Money;
import play.Logger;

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
                return Money.of(CurrencyUnit.EUR, Double.parseDouble(value));
            }
        }
    }
}
