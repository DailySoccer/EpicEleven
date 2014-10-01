package controllers.admin;

import model.*;
import org.bson.types.ObjectId;
import play.data.Form;
import play.mvc.Controller;
import play.mvc.Result;

import java.util.List;

import static play.data.Form.form;

public class ContestEntryController extends Controller {
    public static Result index() {
       return ok(views.html.contest_entry_list.render(ContestEntry.findAllFromContests()));
    }
}
