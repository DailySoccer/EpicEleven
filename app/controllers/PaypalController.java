package controllers;

import actions.AllowCors;
import com.fasterxml.jackson.databind.JsonNode;
import com.mongodb.util.JSON;
import com.paypal.api.payments.*;
import com.paypal.core.rest.PayPalRESTException;
import model.Model;
import model.PaypalPayment;
import model.Product;
import play.Logger;
import play.mvc.BodyParser;
import play.mvc.Controller;
import play.mvc.Result;
import org.bson.types.ObjectId;
import model.Order;

@AllowCors.Origin
public class PaypalController extends Controller {
    // Las rutas relativas al CLIENT a las que enviaremos las respuestas proporcionadas por Paypal
    static final String CLIENT_CANCEL_PATH = "#/payment/response/canceled";
    static final String CLIENT_SUCCESS_PATH = "#/payment/response/success";

    // Las keys que incluimos en las urls de respuesta de Paypal
    static final String QUERY_STRING_SUCCESS_KEY = "success";
    static final String QUERY_STRING_CANCEL_KEY = "cancel";
    static final String QUERY_STRING_ORDER_KEY = "orderId";

    // Los distintos estados posibles de un pago
    static final String PAYMENT_STATE_CREATED = "created";
    static final String PAYMENT_STATE_APPROVED = "approved";
    static final String PAYMENT_STATE_FAILED = "failed";
    static final String PAYMENT_STATE_PENDING = "pending";
    static final String PAYMENT_STATE_CANCELED = "canceled";
    static final String PAYMENT_STATE_EXPIRED = "expired";

    static final String EVENT_PAYMENT_SALE_COMPLETED = "PAYMENT.SALE.COMPLETED";
    static final String EVENT_PAYMENT_SALE_REVERSED = "PAYMENT.SALE.REVERSED";

    // La url a la que redirigimos al usuario cuando el proceso de pago se complete (con éxito o cancelación)
    static final String REFERER_URL_DEFAULT = "epiceleven.com";

    public static Result approvalPayment(String userId, String productId) {
        // Obtenemos desde qué url están haciendo la solicitud
        String refererUrl = request().hasHeader("Referer") ? request().getHeader("Referer") : REFERER_URL_DEFAULT;

        // Si Paypal no responde con un adecuado "approval url", cancelaremos la solicitud
        Result result = null;

        try {
            // Especificar a qué host enviaremos los urls de respuesta
            PaypalPayment.instance().setHostName(request().host());

            // Crear el identificador del nuevo pedido
            ObjectId orderId = new ObjectId();

            // Creamos la solicitud de pago (le proporcionamos el identificador del pedido para referencias posteriores)
            Payment payment = PaypalPayment.instance().createPayment(orderId, Product.findOne(productId));
            Model.paypalResponses().insert(payment.toJSON());

            // Creamos el pedido (con el identificador generado y el de la solicitud de pago)
            //      Únicamente almacenamos el referer si no es el de "por defecto"
            Order.create(orderId, new ObjectId(userId), Order.TransactionType.PAYPAL, payment.getId(), refererUrl);

            String redirectUrl = PaypalPayment.instance().getApprovalURL(payment);
            if (redirectUrl != null) {
                result = redirect(redirectUrl);
            }
        } catch (PayPalRESTException e) {
            Logger.error("WTF 7741: ", e);
        }

        if (result == null) {
            Logger.error("WTF 1209: Paypal: Link approval not found");
            result = redirect(refererUrl + CLIENT_CANCEL_PATH);
        }
        return result;
    }

    /**
     * Recibiremos la respuesta de Paypal. Si es "success" procederemos a "payment.execute"
     *  ?success=true&paymentId=PAY-9AB311545W6759534KRNXYOA&token=EC-9NA34841MA1300346&PayerID=WZADK9MZSSL5N
     *  ?cancel=true
     */
    public static Result executePayment() {
        final String PAYMENT_ID = "paymentId";
        final String PAYER_ID = "PayerID";

        // Identificador del pedido que hemos incluido en las urls de respuesta de Paypal
        String orderId = request().getQueryString(QUERY_STRING_ORDER_KEY);

        // Buscamos el pedido mediante su identificador
        Order order = Order.findOne(orderId);

        // Respuesta "exitosa"?
        boolean success = request().queryString().containsKey(QUERY_STRING_SUCCESS_KEY);
        if (success) {
            // Identificador del pago
            String paymentId = request().getQueryString(PAYMENT_ID);
            if (!order.paymentId.equals(paymentId)) {
                Logger.error("WTF 7743: order.paymentId({}) != paymentId({})", order.paymentId, paymentId);
            }

            // Obtener el identificador del "pagador" (Paypal lo proporciona en la url)
            String payerId = request().getQueryString(PAYER_ID);

            order.setWaitingPayment(payerId);

            try {
                // Completar el pago "ya aprobado" por el pagador
                Payment payment = PaypalPayment.instance().completePayment(paymentId, payerId);
                Model.paypalResponses().insert(payment.toJSON());

                // Evaluar la respuesta de Paypal (values: "created", "approved", "failed", "canceled", "expired", "pending")
                Object response = JSON.parse(payment.toJSON());
                if (payment.getState().equals(PAYMENT_STATE_APPROVED)) {
                    // Pago aprobado
                    order.setCompleted();
                }
                else if (payment.getState().equals(PAYMENT_STATE_PENDING)) {
                    // El pago permanece pendiente de evaluación posterior
                    // TODO: Cómo enterarnos de cuándo lo validan?
                    order.setPending();
                }
                else{
                    // Pago cancelado
                    order.setCanceled();
                    success = false;
                }
            } catch (PayPalRESTException e) {
                Logger.error("WTF 7742: ", e);
            }
        }
        else {
            // Respuesta "cancelada"?
            boolean canceled = request().queryString().containsKey(QUERY_STRING_CANCEL_KEY);
            if (canceled) {
                order.setCanceled();
            }
        }

        String refererUrl = order.referer != null ? order.referer : REFERER_URL_DEFAULT;
        return redirect(refererUrl + (success ? CLIENT_SUCCESS_PATH : CLIENT_CANCEL_PATH));
    }

    public static Result verifyPayment(String paymentId) {
        String response = "";

        try {
            Payment payment = PaypalPayment.instance().verifyPayment(paymentId);
            response = payment.toJSON();

        } catch (PayPalRESTException e) {
            e.printStackTrace();
        }

        return ok(response);
    }

    public static Result history() {
        String response = "";

        try {
            PaymentHistory paymentHistory = PaypalPayment.instance().history();
            response = paymentHistory.toJSON();

        } catch (PayPalRESTException e) {
            e.printStackTrace();
        }

        return ok(response);
    }

    @BodyParser.Of(BodyParser.Json.class)
    public static Result webhook() {
        JsonNode json = request().body().asJson();
        Model.paypalResponses().insert(json);
        Logger.info("webhook: {}", json);

        if (json.get("resource_type").textValue().equalsIgnoreCase("sale")) {
            JsonNode jsonResource = json.findPath("resource");
            if (json.get("event_type").textValue().equalsIgnoreCase("EVENT_PAYMENT_SALE_COMPLETED")) {
                String paymentId = jsonResource.get("parent_payment").textValue();
                String state = jsonResource.get("state").textValue(); // pending; completed; refunded; partially_refunded
                Logger.info("paymentId: {} - state: {}", paymentId, state);
            } else if (json.get("event_type").textValue().equalsIgnoreCase("EVENT_PAYMENT_SALE_REVERSED")) {
                String paymentId = jsonResource.get("parent_payment").textValue();
                String state = jsonResource.get("state").textValue(); // pending; completed; refunded; partially_refunded
                String pendingReason = jsonResource.get("pending_reason").textValue();
                String reasonCode = jsonResource.get("reason_code").textValue();
                Logger.info("paymentId: {} - state: {} - pendingReason: {} - reasonCode: {}",
                        paymentId, state, pendingReason, reasonCode);
            }
        }
        return ok();
    }
}
