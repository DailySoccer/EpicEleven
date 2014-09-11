package controllers.admin;

import model.*;
import org.bson.types.ObjectId;

import java.util.*;

import play.mvc.Controller;
import play.mvc.Result;
import utils.ListUtils;

public class AdminController extends Controller {

    public static Result lobby() {
        // Obtenemos la lista de TemplateContests activos
        List<TemplateContest> templateContests = TemplateContest.findAllActive();

        // Tambien necesitamos devolver todos los concursos instancias asociados a los templates
        List<Contest> contestList = Contest.findAllFromTemplateContests(templateContests);

        // Acceso mediante mapa a los templateContests
        HashMap<ObjectId, TemplateContest> templateContestMap = new HashMap<>();
        for (TemplateContest template : templateContests) {
            templateContestMap.put(template.templateContestId, template);
        }

        return ok(views.html.lobby.render(contestList, templateContestMap));
    }

}