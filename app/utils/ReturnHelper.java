package utils;


import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeCreator;
import com.mongodb.util.JSON;
import com.mongodb.util.JSONParseException;
import model.JSONViews;
import play.Logger;
import play.libs.Json;


public class ReturnHelper {
    static public final String OK = "OK";
    static public final String KO = "KO";

    public String status = OK;
    public Object payload;

    public ReturnHelper(boolean status, Object payload) { this.status = status? OK : KO; this.payload = payload; }
    public ReturnHelper(Object payload) { setPayloadAndStatus(payload); }
    public ReturnHelper() {}

    public JsonNode toJsonNode() {
        try {
            // TODO: Evitar convertir a string para luego convertir a JsonNode
            return Json.parse(new ObjectMapper().writerWithView(JSONViews.Public.class).writeValueAsString(this));
        } catch (JsonProcessingException exc) {
            Logger.error("toJSON: ", exc);
            return null;
        }
    }

    public void setPayloadAndStatus(Object payload) {
        if (payload == null) {
            status = KO;
            this.payload = null;
        }
        else {
            status = OK;
            this.payload = payload;
        }
    }

    public void setOK(Object payload) {
        status = OK;
        this.payload = payload;
    }

    public void setKO(Object payload) {
        status = KO;
        this.payload = payload;
    }
}
