package controllers.admin;

import model.Model;
import model.Promo;
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

public class PromoController extends Controller {
    public static Result index() {
        List<Integer> differentTypes = Model.promos().distinct("eventTypeId").as(Integer.class);
        List<Promo> promoList = new ArrayList<>();
        for (Integer differentType: differentTypes){
            promoList.add(Model.promos().
                    find("{eventTypeId: #}", differentType).sort("{timestamp: -1}").limit(1).
                    as(Promo.class).iterator().next());
        }

        return ok(views.html.promo_list.render(promoList));
    }

    public static Result newForm() {
        Form<PromoForm> promoForm = Form.form(PromoForm.class);
        return ok(promo_add.render(promoForm));
    }

    public static Result edit(String promoId) {
        Promo promo = Promo.findOne(new ObjectId(promoId));
        Form<PromoForm> promoForm = Form.form(PromoForm.class).
                                                            fill(new PromoForm(promo));
        return ok(promo_add.render(promoForm));
    }

    public static Result create() {
        Form<PromoForm> promoForm = form(PromoForm.class).bindFromRequest();
        if (promoForm.hasErrors()) {
            return badRequest(promo_add.render(promoForm));
        }

        PromoForm params = promoForm.get();

        boolean success = params.id.isEmpty()? Promo.createPromo(params.eventType.code, params.points):
                Promo.edit(new ObjectId(params.id), params.points);

        if (!success) {
            FlashMessage.warning("Promo invalid");
            return badRequest(promo_add.render(promoForm));
        }

        return redirect(routes.PromoController.index());
    }
    
}
