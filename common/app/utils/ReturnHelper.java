package utils;


import com.fasterxml.jackson.core.JsonProcessingException;
import model.JsonViews;
import play.Logger;
import play.twirl.api.Content;
import play.mvc.Result;
import play.mvc.Results;


public class ReturnHelper {
    public boolean status = true;
    public Object payload;

    public ReturnHelper(boolean status, Object payload) { this.status = status; this.payload = payload; }
    public ReturnHelper(Object payload) { setPayloadAndStatus(payload); }
    public ReturnHelper() {}

    public Result toResult() {
        return toResult(JsonViews.Public.class);
    }

    public Result toResult(Class jsonView) {

        // Los metodos de Results aceptan o un JsonNode o un Content. Si quisieramos mandar un JsonNode, tendriamos que
        // parsear la jsonPayload con Json.parse(), duplicando el trabajo. Asi que mejor creamos un Content.
        Content ret = null;

        try {
        	if (payload != null) {
            	final String jsonPayload = objectIdMapper.writerWithView(jsonView).writeValueAsString(payload);
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

    static protected ObjectIdMapper objectIdMapper = new ObjectIdMapper();
}
