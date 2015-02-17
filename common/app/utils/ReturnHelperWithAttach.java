package utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import model.JsonViews;
import play.Logger;
import play.mvc.Result;
import play.mvc.Results;
import play.twirl.api.Content;

public class ReturnHelperWithAttach extends ReturnHelper {
    public ReturnHelperWithAttach() {}

    @Override
    public Result toResult() {
        return Results.ok(new Content() {
            @Override public String body() { return jsonDataBuilder.getBody(); }
            @Override public String contentType() { return "application/json"; }
        });
    }

    public ReturnHelperWithAttach attachObject (String key, Object object) {
        return attachObject(key, object, JsonViews.Public.class);
    }

    public ReturnHelperWithAttach attachObject (String key, Object object, Class jsonView) {
        try {
            // Añadimos un JsonData(name, value) al buffer
            jsonDataBuilder.addJsonData(key, objectIdMapper.writerWithView(jsonView).writeValueAsString(object));
        } catch (JsonProcessingException exc) {
            Logger.error("ReturnHelper: attachObject: ", exc);
        }
        return this;
    }

    class JsonDataBuilder {
        public void addJsonData(String name, String value) {
            // Preparamos el buffer (diferenciará entre el primer elemento o los siguientes)
            prepare();

            // Json Data :> "name" : value
            stringBuffer.append("\"").append(name).append("\":");
            stringBuffer.append(value);
        }

        public String getBody() {
            if (stringBuffer == null)
                throw new RuntimeException("WTF 771");

            // Finalizamos el Json Object
            stringBuffer.append("}");

            // Lo convertimos en String
            return stringBuffer.toString();
        }

        private void prepare() {
            // Primer Elemento?
            if (stringBuffer == null) {
                stringBuffer = new StringBuffer();
                stringBuffer.append("{");
            } else {
                // Siguientes...
                stringBuffer.append(",");
            }
        }

        private StringBuffer stringBuffer;
    }

    private JsonDataBuilder jsonDataBuilder = new JsonDataBuilder();
}
