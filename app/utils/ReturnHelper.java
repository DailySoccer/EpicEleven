package utils;


import com.fasterxml.jackson.core.JsonProcessingException;
import model.JsonViews;
import play.Logger;
import play.mvc.Content;
import play.mvc.Result;
import play.mvc.Results;
import java.util.ArrayList;


public class ReturnHelper {
    public boolean status = true;

    public ReturnHelper(boolean status, Object payload) { this.status = status; this.payload = payload; }
    public ReturnHelper(Object payload) { setPayloadAndStatus(payload); }
    public ReturnHelper() {}

    private class JsonEntry {
        public String label;
        public Object payload;
        public JsonEntry(String aLabel, Object aPayload) {
            label = aLabel;
            payload = aPayload;
        }

        public String toJsonString() throws JsonProcessingException {
            String jsonPayload = new ObjectIdMapper().writerWithView(JsonViews.Public.class).writeValueAsString(payload);
            return String.format( "\"%s\" : %s", label, jsonPayload);
        }
    }
    // Dos tipos de resultado: un objeto o una lista de objetos
    private Object payload;
    private ArrayList<JsonEntry> payloadList = new ArrayList<>();

    public Result toResult() {

        // Los metodos de Results aceptan o un JsonNode o un Content. Si quisieramos mandar un JsonNode, tendriamos que
        // parsear la jsonPayload con Json.parse(), duplicando el trabajo. Asi que mejor creamos un Content.
        Content ret = null;

        try {
        	if (payload != null) {
            	final String jsonPayload = payloadToString();

             	ret = new Content() {
                	@Override public String body() { return jsonPayload; }
                	@Override public String contentType() { return "application/json"; }
            	};
            }
        } catch (JsonProcessingException exc) {
            Logger.error("toResult: ", exc);
        }

        if (ret == null)
            return Results.badRequest("{\"error\": \"Server error\"}");

        if (!status)
            return Results.badRequest(ret);

        return Results.ok(ret);
    }

    public void setOK(Object payload) {
        status = true;
        this.payload = payload;
    }

    public void setKO(Object payload) {
        status = false;
        this.payload = payload;
    }

    public void include(String label, Object payload) {
        payloadList.add(new JsonEntry(label, payload));
    }

    private void setPayloadAndStatus(Object payload) {
        if (payload == null) {
            status = false;
            this.payload = null;
        }
        else {
            status = true;
            this.payload = payload;
        }
    }

    private String payloadToString() throws JsonProcessingException {
        String result;
        if (payloadList.isEmpty()) {
            result = new ObjectIdMapper().writerWithView(JsonViews.Public.class).writeValueAsString(payload);
        }
        else {
            result = "{";
            for (JsonEntry data : payloadList) {
                if (result != "{") result += ",\n";
                result += data.toJsonString();
            }
            result += "}";
        }
        return result;
    }
}
