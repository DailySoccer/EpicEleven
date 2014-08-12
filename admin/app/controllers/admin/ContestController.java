package controllers.admin;

import model.Contest;
import model.Model;
import model.ModelEvents;
import org.bson.types.ObjectId;
import play.mvc.Controller;
import play.mvc.Result;
import utils.ListUtils;

import java.util.HashMap;
import java.util.List;

public class ContestController extends Controller {
    public static Result index() {
        Iterable<Contest> contestsResults = Model.contests().find().as(Contest.class);
        List<Contest> contestList = ListUtils.asList(contestsResults);

        return ok(views.html.contest_list.render(contestList));
    }

    public static Result show(String contestId) {
        Contest contest = Model.contests().findOne("{ _id : # }", new ObjectId(contestId)).as(Contest.class);
        HashMap<Integer, String> map = new HashMap<>();
        map.put(0, "hola");
        map.put(1, "adios");
        return ok(views.html.contest.render(contest, map));
    }
}
