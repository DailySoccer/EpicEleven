package controllers.admin;

import model.*;
import model.opta.OptaMatchEvent;
import model.opta.OptaPlayer;
import model.opta.OptaTeam;
import model.opta.OptaEvent;
import org.bson.types.ObjectId;

import java.util.*;

import org.joda.time.DateTime;
import play.Logger;
import play.data.Form;
import play.data.format.Formats;
import play.mvc.Controller;
import play.mvc.Result;
import utils.ListUtils;

import static play.data.Form.form;
import static utils.OptaUtils.recalculateAllEvents;

import play.data.validation.Constraints.Required;
import views.html.points_translation_add;


public class AdminController extends Controller {


    public static Result lobby() {
        Iterable<Contest> contestsResults = Model.contests().find().as(Contest.class);
        List<Contest> contestList = ListUtils.asList(contestsResults);

        HashMap<ObjectId, TemplateContest> templateContestMap = getTemplateContestsFromList(contestList);

        return ok(views.html.lobby.render(contestList, templateContestMap));
    }

    public static HashMap<ObjectId, TemplateContest> getTemplateContestsFromList(List<Contest> contestList) {
        // Obtener la lista de Ids de los TemplateContests
        ArrayList<ObjectId> idsList = new ArrayList<>();
        for (Contest contest : contestList) {
            idsList.add(contest.templateContestId);
        }

        // Obtenemos las lista de TemplateContest de todos los Ids
        Iterable<TemplateContest> templateContestResults = TemplateContest.find("_id", idsList);

        // Convertirlo a map
        HashMap<ObjectId, TemplateContest> ret = new HashMap<>();

        for (TemplateContest template : templateContestResults) {
            ret.put(template.templateContestId, template);
        }

        return ret;
    }

}