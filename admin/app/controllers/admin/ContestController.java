package controllers.admin;

import com.google.common.collect.ImmutableList;
import model.Contest;
import model.Model;
import model.TemplateContest;
import play.mvc.Controller;
import play.mvc.Result;
import utils.PaginationData;

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
                    "",                     // contestEntries.size
                    "maxEntries",
                    "templateContestId",
                    ""                      // templateContest.state
                );
            }

            public String getFieldByIndex(Object data, Integer index) {
                Contest contest = (Contest) data;
                switch (index) {
                    case 0: return contest.name;
                    case 1: return String.valueOf(contest.contestEntries.size());
                    case 2: return String.valueOf(contest.maxEntries);
                    case 3: return contest.templateContestId.toString();
                    case 4: TemplateContest templateContest = TemplateContest.findOne(contest.templateContestId);
                            if(templateContest.isHistory()) {
                                return "Finished";
                            } else if(templateContest.isLive()) {
                                return "Live";
                            } else {
                                return "Waiting";
                            }
                }
                return "<invalid value>";
            }

            public String getRenderFieldByIndex(Object data, String fieldValue, Integer index) {
                Contest contest = (Contest) data;
                switch (index) {
                    case 3: return String.format("<a href=\"%s\">%s</a>",
                                routes.TemplateContestController.show(fieldValue).url(),
                                fieldValue);
                    case 4:
                        if(fieldValue.equals("Finished")) {
                            return "<button class=\"btn btn-danger\">Finished</button>";
                        } else if(fieldValue.equals("Live")) {
                            return "<button class=\"btn btn-success\">Live</button>";
                        } else {
                            return "<button class=\"btn btn-warning\">Waiting</button>";
                        }
                }
                return fieldValue;
            }
        });
    }

    public static Result show(String contestId) {
        return ok(views.html.contest.render(Contest.findOne(contestId)));
    }
}
