package controllers.admin;

import model.ContestEntry;
import play.mvc.Controller;
import play.mvc.Result;

public class ContestEntryController extends Controller {
    public static Result index() {
       return ok(views.html.contest_entry_list.render(ContestEntry.findAllFromContests()));
    }
}
