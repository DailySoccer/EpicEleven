package controllers.admin;

import model.Model;
import model.Promo;
import org.bson.types.ObjectId;
import play.data.Form;
import play.mvc.Controller;
import play.mvc.Result;
import utils.ListUtils;
import views.html.promo_add;

import java.util.List;

import static play.data.Form.form;

public class PromoController extends Controller {
    public static Result index() {
        List<Promo> promoList = ListUtils.asList(Model.promos().find().as(Promo.class));
        return ok(views.html.promo_list.render(promoList));
    }

    public static Result newForm() {
        Form<PromoForm> promoForm = Form.form(PromoForm.class);
        return ok(promo_add.render(promoForm));
    }

    public static Result edit(String promoId) {
        Promo promo = Promo.findOne(new ObjectId(promoId));
        Form<PromoForm> promoForm = Form.form(PromoForm.class).fill(new PromoForm(promo));
        return ok(promo_add.render(promoForm));
    }

    public static Result create() {
        Form<PromoForm> promoForm = form(PromoForm.class).bindFromRequest();
        if (promoForm.hasErrors()) {
            return badRequest(promo_add.render(promoForm));
        }

        PromoForm params = promoForm.get();

        Promo myPromo = new Promo(params.activationDate, params.deactivationDate,
                                params.priority, params.codeName, params.url,
                                params.html, params.imageXs, params.imageDesktop);

        boolean success = params.id.isEmpty()? Promo.createPromo(myPromo):
                                               Promo.edit(new ObjectId(params.id), myPromo);


        if (!success) {
            FlashMessage.warning("Promo invalid");
            return badRequest(promo_add.render(promoForm));
        }

        return redirect(routes.PromoController.index());
    }
    
}
