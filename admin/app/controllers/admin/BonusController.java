package controllers.admin;

import model.bonus.AddFundsBonus;
import model.bonus.SignupBonus;
import play.data.Form;
import play.mvc.Controller;
import play.mvc.Result;
import utils.MoneyUtils;

import static play.data.Form.form;

public class BonusController extends Controller {

    public static Result index() {
        BonusForm params = new BonusForm(SignupBonus.findOne(), AddFundsBonus.findOne());

        Form<BonusForm> bonusForm = Form.form(BonusForm.class).fill(params);

        return ok(views.html.bonus.render(bonusForm));
    }

    public static Result save() {
        Form<BonusForm> bonusForm = form(BonusForm.class).bindFromRequest();
        if (bonusForm.hasErrors()) {
            return badRequest(views.html.bonus.render(bonusForm));
        }

        BonusForm params = bonusForm.get();

        SignupBonus.create(params.signupBonus_activated, MoneyUtils.of(params.signupBonus_money));
        AddFundsBonus.create(params.addFundsBonus_activated,
                MoneyUtils.of(params.addFundsBonus_minMoney), MoneyUtils.of(params.addFundsBonus_maxMoney),
                params.addFundsBonus_multiplier);

        return redirect(routes.BonusController.index());
    }
}
