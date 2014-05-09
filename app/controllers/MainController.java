package controllers;

import model.Model;
import play.*;
import play.libs.Json;
import play.mvc.*;
import play.mvc.Http.RequestBody;
import views.html.*;

public class MainController extends Controller {

    public static class TestClass {
        public String key1;
        public String key2;
    }

    public static Result test() {
        //return ok(index.render("Your new application is ready."));
    	return ok(Json.toJson(new TestClass()));
    }
    
    public static Result ping() {
    	// A little test of reading the application.conf
    	//return ok(ping.render(Play.application().configuration().getString("db.default.url")));
    	return ok("Pong");
    }

    public static Result resetDBActiveContests() {
        Model.resetDBActiveContests();

        return ok();
    }

    @BodyParser.Of(BodyParser.Json.class)
    public static Result jsonTest() {
    	RequestBody body = request().body();
    	return ok("Got json: " + body.asJson());
    }
}
