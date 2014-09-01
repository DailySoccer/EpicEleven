package controllers.admin;

import com.google.common.collect.ImmutableList;
import model.Contest;
import model.Model;
import model.ModelEvents;
import model.TemplateContest;
import org.bson.types.ObjectId;
import play.mvc.Controller;
import play.mvc.Result;
import utils.ListUtils;
import utils.PaginationData;

import java.util.HashMap;
import java.util.List;

public class ContestController extends Controller {
    public static Result index() {
        return ok(views.html.contest_list.render());
    }

    public static Result indexAjax() {
        return PaginationData.withAjax(request().queryString(), Model.contests(), Contest.class, new PaginationData() {
            public List<String> getFieldNames() {
                return ImmutableList.of(
                    "name",
                    "name",
                    "maxEntries",
                    "templateContestId",
                    "name"
                );
            }

            public String getFieldByIndex(Object data, Integer index) {
                Contest contest = (Contest) data;
                switch (index) {
                    case 0: return contest.name;
                    case 1: return String.valueOf(contest.contestEntries.size());
                    case 2: return String.valueOf(contest.maxEntries);
                    case 3: return contest.templateContestId.toString();
                    case 4: return "State";
                }
                return "<invalid value>";
            }

            public String getFieldHtmlByIndex(Object data, Integer index) {
                Contest contest = (Contest) data;
                switch (index) {
                    case 0: return contest.name;
                    case 1: return String.valueOf(contest.contestEntries.size());
                    case 2: return String.valueOf(contest.maxEntries);
                    case 3: return contest.templateContestId.toString();
                    case 4:
                        TemplateContest templateContest = TemplateContest.findOne(contest.templateContestId);
                        if(templateContest.isFinished()) {
                            return "<button class=\"btn btn-danger\">Finished</button>";
                        } else if(templateContest.isStarted()) {
                            return "<button class=\"btn btn-success\">Live</button>";
                        } else {
                            return "<button class=\"btn btn-warning\">Waiting</button>";
                        }
                }
                return "<invalid value>";
            }
        });
    }

    public static Result show(String contestId) {
        Contest contest = Model.contests().findOne("{ _id : # }", new ObjectId(contestId)).as(Contest.class);
        HashMap<Integer, String> map = new HashMap<>();
        map.put(0, "hola");
        map.put(1, "adios");
        return ok(views.html.contest.render(contest, map));
    }
}
