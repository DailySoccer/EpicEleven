package controllers.admin;

import com.google.common.collect.ImmutableList;
import model.Contest;
import model.Model;
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
                    "optaCompetitionId",
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
                    case 4: return contest.optaCompetitionId;
                    case 5: if(contest.isHistory()) {
                                return "Finished";
                            } else if(contest.isCanceled()) {
                                return "Canceled";
                            } else if(contest.isLive()) {
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
                    case 5:
                        if(fieldValue.equals("Finished")) {
                            return "<button class=\"btn btn-danger\">Finished</button>";
                        } else if(fieldValue.equals("Canceled")) {
                            return "<button class=\"btn btn-danger\">Canceled</button>";
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
