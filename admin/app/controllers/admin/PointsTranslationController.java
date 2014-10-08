package controllers.admin;

import model.Model;
import model.PointsTranslation;
import org.bson.types.ObjectId;
import play.Logger;
import play.data.Form;
import play.mvc.Controller;
import play.mvc.Result;
import utils.ListUtils;
import views.html.points_translation_add;

import java.util.ArrayList;
import java.util.List;

import static play.data.Form.form;

public class PointsTranslationController extends Controller {
    public static Result index() {
        List<Integer> differentTypes = Model.pointsTranslation().distinct("eventTypeId").as(Integer.class);
        List<PointsTranslation> pointsTranslationList = new ArrayList<PointsTranslation>();
        for (Integer differentType: differentTypes){
            pointsTranslationList.add((PointsTranslation)Model.pointsTranslation().
                    find("{eventTypeId: #}", differentType).sort("{timestamp: -1}").limit(1).
                    as(PointsTranslation.class).iterator().next());
        }

        return ok(views.html.points_translation_list.render(pointsTranslationList));
    }

    public static Result newForm() {
        Form<PointsTranslationForm> pointsTranslationForm = Form.form(PointsTranslationForm.class);
        return ok(points_translation_add.render(pointsTranslationForm));
    }

    public static Result edit(String pointsTranslationId) {
        PointsTranslation pointsTranslation = PointsTranslation.findOne(new ObjectId(pointsTranslationId));
        Form<PointsTranslationForm> pointsTranslationForm = Form.form(PointsTranslationForm.class).
                                                            fill(new PointsTranslationForm(pointsTranslation));
        return ok(points_translation_add.render(pointsTranslationForm));
    }

    public static Result resetToDefault(){
        Model.pointsTranslation().remove();
        PointsTranslation.createDefault();
        return redirect(routes.PointsTranslationController.index());
    }

    public static Result create() {
        Form<PointsTranslationForm> pointsTranslationForm = form(PointsTranslationForm.class).bindFromRequest();
        if (pointsTranslationForm.hasErrors()) {
            return badRequest(views.html.points_translation_add.render(pointsTranslationForm));
        }

        PointsTranslationForm params = pointsTranslationForm.get();

        boolean success = params.id.isEmpty()? PointsTranslation.createPointForEvent(params.eventType.code, params.points):
                PointsTranslation.editPointForEvent(new ObjectId(params.id), params.points);

        if (!success) {
            FlashMessage.warning("Points Translation invalid");
            return badRequest(views.html.points_translation_add.render(pointsTranslationForm));
        }

        Logger.info("Event Type ({}) = {} points", params.eventType, params.points);

        return redirect(routes.PointsTranslationController.index());
    }

    public static Result history(int eventType) {
        Iterable<PointsTranslation> pointsTranslationList = Model.pointsTranslation().find("{eventTypeId: #}", eventType).
                sort("{timestamp: -1}").as(PointsTranslation.class);

        List<PointsTranslation> pointsTranslationResult = ListUtils.asList(pointsTranslationList);
        return ok(views.html.points_translation_list.render(pointsTranslationResult));
    }
}
