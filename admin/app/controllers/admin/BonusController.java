package controllers.admin;

import model.bonus.AddFundsBonus;
import model.bonus.SignupBonus;
import org.joda.money.Money;
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

        SignupBonus.create(params.signupBonus_activated, Money.of(MoneyUtils.CURRENCY_GOLD, params.signupBonus_gold), Money.of(MoneyUtils.CURRENCY_MANAGER, params.signupBonus_manager));
        AddFundsBonus.create(params.addFundsBonus_activated,
                Money.of(MoneyUtils.CURRENCY_GOLD, params.addFundsBonus_minMoney), Money.of(MoneyUtils.CURRENCY_GOLD, params.addFundsBonus_maxMoney),
                params.addFundsBonus_percent);

        FlashMessage.info("Bonus saved");

        return redirect(routes.BonusController.index());
    }
}
