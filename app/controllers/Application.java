package controllers;

import play.*;
import play.libs.Json;
import play.mvc.*;
import play.mvc.Http.RequestBody;
import views.html.*;

class TestClass {
	public String var1 = "Hello";
	public String var2 = "Bye";
}

public class Application extends Controller {

    public static Result test() {
        //return ok(index.render("Your new application is ready."));
    	return ok(Json.toJson(new TestClass()));
    }
    
    public static Result ping() {
    	// A little test of reading the application.conf
    	return ok(ping.render(Play.application().configuration().getString("db.default.url")));
    	//return ok("Pong " + dbUrl);
    }

    @BodyParser.Of(BodyParser.Json.class)
    public static Result jsontest() {
    	RequestBody body = request().body();
    	return ok("Got json: " + body.asJson());
    }
}
