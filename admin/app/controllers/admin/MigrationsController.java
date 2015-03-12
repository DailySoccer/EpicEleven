package controllers.admin;

import play.mvc.Controller;
import play.mvc.Result;
import utils.Migrations;

public class MigrationsController extends Controller {
    public static Result index() {
        return ok(views.html.migrations.render(Migrations.evaluate()));
    }

    public static Result apply(String type) {
        Migrations.apply(type);
        return redirect(routes.MigrationsController.index());
    }
}
