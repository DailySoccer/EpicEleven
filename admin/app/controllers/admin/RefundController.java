package controllers.admin;

import model.Model;
import model.Refund;
import play.mvc.Controller;
import play.mvc.Result;
import utils.ListUtils;

import java.util.List;

public class RefundController extends Controller {

    public static Result index() {
        List<Refund> refunds = ListUtils.asList(Model.refunds().find().as(Refund.class));
        return ok(views.html.refund_list.render(refunds));
    }

    public static Result apply(String refundId) {
        Refund refund = Refund.findOne(refundId);
        refund.apply();
        return redirect(routes.RefundController.index());
    }
}
