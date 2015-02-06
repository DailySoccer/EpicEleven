package model;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import org.joda.money.Money;

import java.io.IOException;

public class JacksonJodaMoney {
    public static class MoneySerializer extends StdSerializer<Money> {
        protected MoneySerializer() {
            super(Money.class);
        }

        @Override
        public void serialize(Money value, JsonGenerator jgen, SerializerProvider provider) throws IOException {
            jgen.writeString(value.toString());
        }
    }

    public static class MoneyDeserializer extends StdDeserializer<Money> {
        protected MoneyDeserializer() {
            super(Money.class);
        }

        @Override
        public Money deserialize(JsonParser jp, DeserializationContext ctxt) throws IOException {
            return Money.parse(jp.readValueAs(String.class));
        }
    }
}
