package controllers;

import actions.AllowCors;
import actions.UserAuthenticated;
import com.mongodb.util.JSON;
import com.paypal.api.payments.*;
import com.paypal.core.rest.APIContext;
import com.paypal.core.rest.OAuthTokenCredential;
import com.paypal.core.rest.PayPalRESTException;
import model.User;
import play.Logger;
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

    static final String CURRENCY = "EUR";
    static final String CLIENT_ID = "AXGKyxAeNjwaGg4gNwHDEoidWC7_uQeRgaFAWTccuLqb1-R-s11FWbceSWR0";
    static final String SECRET = "ENlBYxDHZVn_hotpxYtCXD3NPvvPQSmj8CbfzYWZyaFddkQTwhhw3GxV5Ipe";
    static final String CANCEL_URL = "https://devtools-paypal.com/guide/pay_paypal?cancel=true&orderId=";
    static final String RETURN_URL = "https://devtools-paypal.com/guide/pay_paypal?success=true&orderId=";

    static final String QUERY_STRING_SUCCESS_KEY = "success";
    static final String QUERY_STRING_CANCEL_KEY = "cancel";
    static final String QUERY_STRING_ORDER_KEY = "orderId";

    static final String LINK_APPROVAL_URL = "approval_url";

    static final String PAYMENT_STATE_CREATED = "created";
    static final String PAYMENT_STATE_APPROVED = "approved";
    static final String PAYMENT_STATE_FAILED = "failed";
    static final String PAYMENT_STATE_PENDING = "pending";
    static final String PAYMENT_STATE_CANCELED = "canceled";
    static final String PAYMENT_STATE_EXPIRED = "expired";

    static final String REFERER_URL_DEFAULT = "www.epiceleven.com";
    static final String RESPONSE_PATH = "#/payment/response/";
    static final String RESPONSE_SUCCESS = "success";
    static final String RESPONSE_CANCELED = "canceled";

    static String refererUrl = REFERER_URL_DEFAULT;

    public static Result approvalPayment(String userId, int money) {
        refererUrl = request().hasHeader("Referer") ? request().getHeader("Referer") : REFERER_URL_DEFAULT;

        Map<String, String> sdkConfig = getSdkConfig();

        Result result = badRequest();

        try {
            String accessToken = new OAuthTokenCredential(CLIENT_ID, SECRET, sdkConfig).getAccessToken();
            ObjectId orderId = new ObjectId();

            Payment payment = createPayment(accessToken, orderId, "creating a payment", money);
            Logger.info("payment.create: {}", payment.toJSON());

            Order.create(orderId, new ObjectId(userId), Order.TransactionType.PAYPAL, payment.getId(), JSON.parse(payment.toJSON()));

            List<Links> links = payment.getLinks();
            for (Links link: links) {
                if (link.getRel().equals(LINK_APPROVAL_URL)) {
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

        boolean success = request().queryString().containsKey(QUERY_STRING_SUCCESS_KEY);
        if (success) {
            String paymentID = request().getQueryString(PAYMENT_ID);
            String payerId = request().getQueryString(PAYER_ID);

            Order order = Order.findOneFromPayment(paymentID);
            order.waitingPayment(payerId);

            try {
                String accessToken = new OAuthTokenCredential(CLIENT_ID, SECRET, sdkConfig).getAccessToken();
                Payment payment = executePayment(sdkConfig, accessToken, paymentID, payerId);
                Logger.info("payment.execute: {}", payment.toJSON());

                Object response = JSON.parse(payment.toJSON());
                if (payment.getState().equals(PAYMENT_STATE_APPROVED)) {
                    order.completed(response);
                }
                else if (payment.getState().equals(PAYMENT_STATE_PENDING)) {
                    order.pending(response);
                }
                else{
                    order.canceled(response);
                }
            } catch (PayPalRESTException e) {
                e.printStackTrace();
            }
        }
        else {
            boolean canceled = request().queryString().containsKey(QUERY_STRING_CANCEL_KEY);
            if (canceled) {
                String orderId = request().getQueryString(QUERY_STRING_ORDER_KEY);

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

    private static Payment createPayment(String accessToken, ObjectId orderId, String description, int money) {
        Map<String, String> sdkConfig = getSdkConfig();

        APIContext apiContext = new APIContext(accessToken);
        apiContext.setConfigurationMap(sdkConfig);

        Amount amount = new Amount();
        amount.setCurrency(CURRENCY);
        amount.setTotal(String.valueOf(money));

        Transaction transaction = new Transaction();
        transaction.setDescription(description);
        transaction.setAmount(amount);

        List<Transaction> transactions = new ArrayList<>();
        transactions.add(transaction);

        Payer payer = new Payer();
        payer.setPaymentMethod("paypal");

        Payment payment = new Payment();
        payment.setIntent("sale");
        payment.setPayer(payer);
        payment.setTransactions(transactions);
        RedirectUrls redirectUrls = new RedirectUrls();
        redirectUrls.setCancelUrl(CANCEL_URL + orderId.toString() );
        redirectUrls.setReturnUrl(RETURN_URL + orderId.toString() );
        payment.setRedirectUrls(redirectUrls);

        Payment createdPayment = null;
        try {
            createdPayment = payment.create(apiContext);
        } catch (PayPalRESTException e) {
            e.printStackTrace();
        }
        return createdPayment;
    }

    private static Payment executePayment(Map<String, String> sdkConfig, String accessToken, String paymentId, String payerId) {
        APIContext apiContext = new APIContext(accessToken);
        apiContext.setConfigurationMap(sdkConfig);

        Payment payment = new Payment();
        payment.setId(paymentId);

        PaymentExecution paymentExecute = new PaymentExecution();
        paymentExecute.setPayerId(payerId);

        Payment paymentResult = null;
        try {
            paymentResult = payment.execute(apiContext, paymentExecute);
        } catch (PayPalRESTException e) {
            e.printStackTrace();
        }
        return paymentResult;
    }
}
