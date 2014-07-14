package controllers.admin;

import model.*;
import org.bson.types.ObjectId;

import java.util.*;

import play.mvc.Controller;
import play.mvc.Result;
import utils.ListUtils;

public class AdminController extends Controller {


    public static Result lobby() {
        Iterable<Contest> contestsResults = Model.contests().find().as(Contest.class);
        List<Contest> contestList = ListUtils.asList(contestsResults);

        HashMap<ObjectId, TemplateContest> templateContestMap = getTemplateContestsFromList(contestList);

        return ok(views.html.lobby.render(contestList, templateContestMap));
    }

    public static HashMap<ObjectId, TemplateContest> getTemplateContestsFromList(List<Contest> contestList) {

        Iterable<TemplateContest> templateContestResults = TemplateContest.findAllFromContests(contestList);

        // Convertirlo a map
        HashMap<ObjectId, TemplateContest> ret = new HashMap<>();

        for (TemplateContest template : templateContestResults) {
            ret.put(template.templateContestId, template);
        }

        return ret;
    }

}