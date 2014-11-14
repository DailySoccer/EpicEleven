package controllers;

import actions.AllowCors;
import com.mongodb.util.JSON;
import com.paypal.api.payments.*;
import com.paypal.core.rest.APIContext;
import com.paypal.core.rest.OAuthTokenCredential;
import com.paypal.core.rest.PayPalRESTException;
import play.mvc.Controller;
import play.mvc.Result;
import org.bson.types.ObjectId;
import model.Order;

import java.util.*;

@AllowCors.Origin
public class PaypalController extends Controller {
    static final String MODE_SANDBOX = "sandbox";
    static final String MODE_LIVE = "live";
    static final String MODE_CONFIG = MODE_SANDBOX;

    static final String CANCEL_PATH = "/paypal/execute_payment?cancel=true&orderId=";
    static final String RETURN_PATH = "/paypal/execute_payment?success=true&orderId=";

    /*
        LIVE CONFIGURATION
     */
    static final String LIVE_CLIENT_ID = "AXGKyxAeNjwaGg4gNwHDEoidWC7_uQeRgaFAWTccuLqb1-R-s11FWbceSWR0";
    static final String LIVE_SECRET = "ENlBYxDHZVn_hotpxYtCXD3NPvvPQSmj8CbfzYWZyaFddkQTwhhw3GxV5Ipe";
    static final String LIVE_CANCEL_URL = "http://backend.epiceleven.com" + CANCEL_PATH;
    static final String LIVE_RETURN_URL = "http://backend.epiceleven.com" + RETURN_PATH;

    /*
        SANDBOX CONFIGURATION
     */
    static final String SANDBOX_CLIENT_ID = "AXGKyxAeNjwaGg4gNwHDEoidWC7_uQeRgaFAWTccuLqb1-R-s11FWbceSWR0";
    static final String SANDBOX_SECRET = "ENlBYxDHZVn_hotpxYtCXD3NPvvPQSmj8CbfzYWZyaFddkQTwhhw3GxV5Ipe";
    static final String SANDBOX_CANCEL_URL = "https://devtools-paypal.com/guide/pay_paypal?cancel=true&orderId=";
    static final String SANDBOX_RETURN_URL = "https://devtools-paypal.com/guide/pay_paypal?success=true&orderId=";

    static final String CURRENCY = "EUR";

    // Las keys que incluimos en las urls de respuesta de Paypal
    static final String QUERY_STRING_SUCCESS_KEY = "success";
    static final String QUERY_STRING_CANCEL_KEY = "cancel";
    static final String QUERY_STRING_ORDER_KEY = "orderId";

    // Identificador de un Link enviado por Paypal para proceder a la "aprobación" del pago por parte del pagador
    static final String LINK_APPROVAL_URL = "approval_url";

    // Los distintos estados posibles de un pago
    static final String PAYMENT_STATE_CREATED = "created";
    static final String PAYMENT_STATE_APPROVED = "approved";
    static final String PAYMENT_STATE_FAILED = "failed";
    static final String PAYMENT_STATE_PENDING = "pending";
    static final String PAYMENT_STATE_CANCELED = "canceled";
    static final String PAYMENT_STATE_EXPIRED = "expired";

    // La url a la que redirigimos al usuario cuando el proceso de pago se complete (con éxito o cancelación)
    static final String REFERER_URL_DEFAULT = "www.epiceleven.com";
    // Lo que añadiremos a la url para informar al webclient
    static final String RESPONSE_PATH = "#/payment/response/";
    static final String RESPONSE_SUCCESS = "success";
    static final String RESPONSE_CANCELED = "canceled";

    static String refererUrl = REFERER_URL_DEFAULT;

    public static Result approvalPayment(String userId, int money) {
        // Obtenemos desde qué url están haciendo la solicitud
        refererUrl = request().hasHeader("Referer") ? request().getHeader("Referer") : REFERER_URL_DEFAULT;

        Map<String, String> sdkConfig = getSdkConfig();

        // Si Paypal no responde con un adecuado "approval url", cancelaremos la solicitud
        Result result = redirect(refererUrl + RESPONSE_PATH + RESPONSE_CANCELED);

        try {
            // Obtener la autorización de Paypal a nuestra cuenta
            String accessToken = getAccessToken(sdkConfig);

            // Crear el identificador del nuevo pedido
            ObjectId orderId = new ObjectId();

            // Creamos la solicitud de pago (le proporcionamos el identificador del pedido para referencias posteriores)
            Payment payment = createPayment(accessToken, orderId, "creating a payment", money);
            // Logger.info("payment.create: {}", payment.toJSON());

            // Creamos el pedido (con el identificador generado y el de la solicitud de pago)
            Order.create(orderId, new ObjectId(userId), Order.TransactionType.PAYPAL, payment.getId(), JSON.parse(payment.toJSON()));

            // Buscamos la url para conseguir la "aprobación" del pagador
            List<Links> links = payment.getLinks();
            for (Links link: links) {
                if (link.getRel().equals(LINK_APPROVAL_URL)) {
                    // Redirigimos al pagador a Paypal para que se identifique y gestione el pago
                    result = redirect(link.getHref());
                    break;
                }
            }
        } catch (PayPalRESTException e) {
            e.printStackTrace();
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

        Map<String, String> sdkConfig = getSdkConfig();

        // Identificador del pedido que hemos incluido en las urls de respuesta de Paypal
        String orderId = request().getQueryString(QUERY_STRING_ORDER_KEY);

        // Respuesta "exitosa"?
        boolean success = request().queryString().containsKey(QUERY_STRING_SUCCESS_KEY);
        if (success) {
            // Identificador del pago
            String paymentID = request().getQueryString(PAYMENT_ID);

            // Obtener el identificador del "pagador" (Paypal lo proporciona en la url)
            String payerId = request().getQueryString(PAYER_ID);

            // Obtenemos el pedido (podemos hacerlo mediante el identificador del pago o por el orderId)
            Order order = Order.findOneFromPayment(paymentID);
            order.waitingPayment(payerId);

            try {
                // Obtener la autorización de Paypal a nuestra cuenta
                String accessToken = getAccessToken(sdkConfig);

                // Completar el pago "ya aprobado" por el pagador
                Payment payment = completePayment(sdkConfig, accessToken, paymentID, payerId);
                // Logger.info("payment.execute: {}", payment.toJSON());

                // Evaluar la respuesta de Paypal (values: "created", "approved", "failed", "canceled", "expired", "pending")
                Object response = JSON.parse(payment.toJSON());
                if (payment.getState().equals(PAYMENT_STATE_APPROVED)) {
                    // Pago aprobado
                    order.completed(response);
                }
                else if (payment.getState().equals(PAYMENT_STATE_PENDING)) {
                    // El pago permanece pendiente de evaluación posterior
                    // TODO: Cómo enterarnos de cuándo lo validan?
                    order.pending(response);
                }
                else{
                    // Pago cancelado
                    order.canceled(response);
                }
            } catch (PayPalRESTException e) {
                e.printStackTrace();
            }
        }
        else {
            // Respuesta "cancelada"?
            boolean canceled = request().queryString().containsKey(QUERY_STRING_CANCEL_KEY);
            if (canceled) {
                // Buscamos el pedido mediante su identificador
                Order order = Order.findOne(orderId);
                order.canceled(null);
            }
        }

        return redirect(refererUrl + RESPONSE_PATH + (success ? RESPONSE_SUCCESS : RESPONSE_CANCELED));
    }

    private static Map<String, String> getSdkConfig() {
        Map<String, String> sdkConfig = new HashMap<>();
        sdkConfig.put("mode", MODE_CONFIG);
        return sdkConfig;
    }

    /**
     *
     * @param accessToken   Autorización de acceso proporcionado por Paypal
     * @param orderId       Identificador del pedido. Se incluirá en la url de respuesta de Paypal para reconocer a qué pedido hace referencia
     * @param description   Texto asociado al pago
     * @param money         Dinero que solicitamos
     * @return Respuesta dada por Paypal (podría ser "aprobada", "quedarse pendiente" o "cancelada")
     */
    private static Payment createPayment(String accessToken, ObjectId orderId, String description, int money) {
        Map<String, String> sdkConfig = getSdkConfig();

        APIContext apiContext = new APIContext(accessToken);
        apiContext.setConfigurationMap(sdkConfig);

        // Moneda usada y dinero
        Amount amount = new Amount();
        amount.setCurrency(CURRENCY);
        amount.setTotal(String.valueOf(money));

        // Descripción (127 caracteres max.) y Cantidad solicitada
        Transaction transaction = new Transaction();
        transaction.setDescription(description);
        transaction.setAmount(amount);

        // Detalles de la transacción
        List<Transaction> transactions = new ArrayList<>();
        transactions.add(transaction);

        // Solicitamos pago mediante "paypal" (values: "paypal", "credit_card")
        //    - Direct Credit Card Payments: únicamente disponible en "United States" y "United Kingdom"
        Payer payer = new Payer();
        payer.setPaymentMethod("paypal");

        // Solicitud de  pago "inmediato" "sale" (values: "sale", "authorize", "order")
        Payment payment = new Payment();
        payment.setIntent("sale");
        payment.setPayer(payer);
        payment.setTransactions(transactions);
        payment.setRedirectUrls(getRedirectUrls(sdkConfig, orderId));

        // Solicitud de "aprobación" a Paypal
        Payment createdPayment = null;
        try {
            createdPayment = payment.create(apiContext);
        } catch (PayPalRESTException e) {
            e.printStackTrace();
        }
        return createdPayment;
    }

    private static Payment completePayment(Map<String, String> sdkConfig, String accessToken, String paymentId, String payerId) {
        APIContext apiContext = new APIContext(accessToken);
        apiContext.setConfigurationMap(sdkConfig);

        // Proporcionamos el identificador del pago
        Payment payment = new Payment();
        payment.setId(paymentId);

        // Proporcionar el id del "pagador" (proporcionado en el "return_url" por Paypal)
        PaymentExecution paymentExecute = new PaymentExecution();
        paymentExecute.setPayerId(payerId);

        Payment paymentResult = null;
        try {
            // Procedemos a solicitar a Paypal que lleve a cabo el pago, para que el dinero pase de una cuenta a otra
            paymentResult = payment.execute(apiContext, paymentExecute);
        } catch (PayPalRESTException e) {
            e.printStackTrace();
        }
        return paymentResult;
    }

    private static RedirectUrls getRedirectUrls(Map<String, String> sdkConfig, ObjectId orderId) {
        // Incluir en las urls el identificador del pedido
        RedirectUrls redirectUrls = new RedirectUrls();
        if (isLive(sdkConfig)) {
            redirectUrls.setCancelUrl(LIVE_CANCEL_URL + orderId.toString());
            redirectUrls.setReturnUrl(LIVE_RETURN_URL + orderId.toString());
        }
        else {
            // Solicitud de pago desde un "localHost"?
            if (refererUrl.contains("localhost") || refererUrl.contains("127.")) {
                // Las urls de respuesta serán las de Paypal...
                redirectUrls.setCancelUrl(SANDBOX_CANCEL_URL + orderId.toString());
                redirectUrls.setReturnUrl(SANDBOX_RETURN_URL + orderId.toString());
            }
            else {
                // Le redirigimos a la url desde la que vino la solicitud
                redirectUrls.setCancelUrl(refererUrl + CANCEL_PATH + orderId.toString());
                redirectUrls.setReturnUrl(refererUrl + RETURN_PATH + orderId.toString());
            }
        }
        return redirectUrls;
    }

    private static String getAccessToken(Map<String, String> sdkConfig) throws PayPalRESTException {
        return isLive(sdkConfig)
                    ? new OAuthTokenCredential(LIVE_CLIENT_ID, LIVE_SECRET, sdkConfig).getAccessToken()
                    : new OAuthTokenCredential(SANDBOX_CLIENT_ID, SANDBOX_SECRET, sdkConfig).getAccessToken();
    }

    private static boolean isLive(Map<String, String> sdkConfig) {
        return sdkConfig.get("mode").equals(MODE_LIVE);
    }
}
