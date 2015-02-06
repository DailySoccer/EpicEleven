package utils;

import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.databind.*;
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
            try {
                return Money.parse(jsonParser.readValueAs(String.class));
            }
            catch (Exception e) {
                return Money.zero(CurrencyUnit.EUR);
            }
        }
    }
}
