package utils;


import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;


public class ReturnHelper {
    static public final String OK = "OK";
    static public final String KO = "KO";

    public String status = OK;
    public Object payload;

    public ReturnHelper(boolean status, Object payload) { this.status = status? OK : KO; this.payload = payload; }
    public ReturnHelper(Object payload) { setPayloadAndStatus(payload); }
    public ReturnHelper() {}

    public JsonNode toJSON() {
        return new ObjectMapper().valueToTree(this);
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
