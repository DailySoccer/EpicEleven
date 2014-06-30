package controllers.admin;

import model.MockData;
import model.Model;
import play.mvc.Controller;
import play.mvc.Result;
import play.mvc.Results;

public class DashboardController extends Controller {
    public static Result index() {
        return Results.ok(views.html.dashboard.render());
    }

    public static Result resetDB() {
        Model.resetDB();

        FlashMessage.success("Reset DB: OK");
        return Results.ok(views.html.dashboard.render());
    }

    public static Result createMockDataDB() {
        Model.resetDB();
        MockData.ensureMockDataAll();

        FlashMessage.success("Reset DB with Mock Data");
        return Results.ok(views.html.dashboard.render());
    }

    public static Result resetContests() {
        Model.resetContests();

        FlashMessage.success("Reset Contests: OK");
        return Results.ok(views.html.dashboard.render());
    }

    public static Result createMockDataContests() {
        Model.resetContests();
        MockData.ensureMockDataContests();

        FlashMessage.success("Reset Contests with Mock data: OK");
        return Results.ok(views.html.dashboard.render());
    }
}
