package controllers.admin;

import model.Contest;
import model.Model;
import model.TemplateContest;
import org.bson.types.ObjectId;
import play.mvc.Controller;
import play.mvc.Result;

import java.util.HashMap;
import java.util.List;

public class LobbyController extends Controller {

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