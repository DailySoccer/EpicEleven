package controllers;

import actions.AllowCors;
import actions.UserAuthenticated;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import model.Model;
import model.User;
import model.jobs.CompleteOrderJob;
import model.shop.Catalog;
import model.shop.Order;
import model.shop.ProductMoney;
import org.bson.types.ObjectId;
import play.Logger;
import play.data.Form;
import play.data.validation.Constraints;
import play.mvc.Controller;
import play.mvc.Result;
import utils.ReturnHelper;

import static play.data.Form.form;

@AllowCors.Origin
public class StoreController extends Controller {

    private static final String ERROR_KEY = "error";
    private static final String ERROR_PRODUCT_INVALID = "ERROR_PRODUCT_INVALID";

    public static class BuyParams {
        @Constraints.Required
        public String productId;

        @Constraints.Required
        public String paymentType;

        @Constraints.Required
        public String paymentId;
    }

    @UserAuthenticated
    public static Result buy(String paymentId) {
        User theUser = (User) ctx().args.get("User");

        Form<BuyParams> buyForm = form(BuyParams.class).bindFromRequest();

        if (!buyForm.hasErrors()) {
            BuyParams params = buyForm.get();

            Order.TransactionType transactionType = params.paymentType.contains("android") ? Order.TransactionType.PLAYSTORE : Order.TransactionType.ITUNES_CONNECT;

            Logger.debug("{}: User: {} Type: {} Product: {} PaymentId: {}", transactionType, theUser.userId.toString(), params.paymentType, params.productId, params.paymentId);

            Order order = Order.findOneFromPayment(transactionType, params.paymentId);
            if (order == null) {
                // Obtenemos desde qué url están haciendo la solicitud
                String refererUrl = request().hasHeader("Referer") ? request().getHeader("Referer") : Order.REFERER_URL_DEFAULT;

                // Crear el identificador del nuevo pedido
                ObjectId orderId = new ObjectId();

                // Producto que quiere comprar
                ProductMoney product = (ProductMoney) Catalog.findOne(params.productId);
                if (product == null) {
                    buyForm.reject(ERROR_KEY, ERROR_PRODUCT_INVALID);
                }

                if (!buyForm.hasErrors()) {
                    // Creamos el pedido (con el identificador generado y el de la solicitud de pago)
                    order = Order.create(
                            orderId,
                            theUser.userId,
                            transactionType,
                            params.paymentId,
                            ImmutableList.of(product),
                            Order.REFERER_URL_DEFAULT);

                    CompleteOrderJob job = CompleteOrderJob.create(order.orderId);

                    Logger.debug("{}: New Order: {} PaymentId: {} State: {}", transactionType, order.orderId.toString(), params.paymentId, job.state);
                }
            }
            else {
                Logger.debug("{}: Order: {} ya registrada para ese PaymentId: {}", transactionType, order.orderId.toString(), params.paymentId);
            }
        }

        Object result = buyForm.errorsAsJson();

        if (!buyForm.hasErrors()) {
            result = ImmutableMap.of(
                    "result", "ok",
                    "profile", User.findOne(theUser.userId).getProfile()
            );
        }

        return new ReturnHelper(!buyForm.hasErrors(), result).toResult();
    }

}

