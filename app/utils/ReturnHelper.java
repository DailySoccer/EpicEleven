package utils;


import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import model.JsonViews;
import play.Logger;
import play.libs.Json;


public class ReturnHelper {
    public boolean status = true;
    public Object payload;

    public ReturnHelper(boolean status, Object payload) { this.status = status; this.payload = payload; }
    public ReturnHelper(Object payload) { setPayloadAndStatus(payload); }
    public ReturnHelper() {}

    public JsonNode toJsonNode() {
        try {
            // TODO: Evitar convertir a string para luego convertir a JsonNode
            return Json.parse(new ObjectMapper().writerWithView(JsonViews.Public.class).writeValueAsString(payload));
        } catch (JsonProcessingException exc) {
            Logger.error("toJSON: ", exc);
            return null;
        }
    }

    public void setOK(Object payload) {
        status = true;
        this.payload = payload;
    }

    public void setKO(Object payload) {
        status = false;
        this.payload = payload;
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
}
