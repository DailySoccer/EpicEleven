package controllers;

import com.paypal.api.payments.*;
import com.paypal.core.rest.APIContext;
import com.paypal.core.rest.OAuthTokenCredential;
import com.paypal.core.rest.PayPalRESTException;
import play.Logger;
import play.mvc.Controller;
import play.mvc.Result;

import java.util.*;

public class PaypalController extends Controller {
    static final String MODE_SANDBOX = "sandbox";
    static final String MODE_LIVE = "live";
    static final String MODE_CONFIG = MODE_SANDBOX;

    static final String CURRENCY = "EUR";
    static final String CLIENT_ID = "";
    static final String SECRET = "";
    static final String CANCEL_URL = "https://devtools-paypal.com/guide/pay_paypal?cancel=true";
    static final String RETURN_URL = "https://devtools-paypal.com/guide/pay_paypal?success=true";

    static final String LINK_APPROVAL_URL = "approval_url";


    public static Result init() {
        Map<String, String> sdkConfig = getSdkConfig();

        Result result = ok("Ok");

        try {
            String accessToken = new OAuthTokenCredential(CLIENT_ID, SECRET, sdkConfig).getAccessToken();
            Payment payment = createPayment(accessToken, "creating a payment", 12);
            Logger.info("payment.create: {}", payment.toJSON());

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
    public static Result execute() {
        final String PAYMENT_ID = "paymentId";
        final String PAYER_ID = "PayerID";

        Map<String, String> sdkConfig = getSdkConfig();

        boolean success = request().queryString().containsKey("success");
        if (success) {
            String paymentID = request().getQueryString(PAYMENT_ID);
            String payerId = request().getQueryString(PAYER_ID);

            try {
                String accessToken = new OAuthTokenCredential(CLIENT_ID, SECRET, sdkConfig).getAccessToken();
                Payment payment = executePayment(sdkConfig, accessToken, paymentID, payerId);
                Logger.info("payment.execute: {}", payment.toJSON());
            } catch (PayPalRESTException e) {
                e.printStackTrace();
            }
        }

        return success ? ok("Ok") : badRequest();
    }

    private static Map<String, String> getSdkConfig() {
        Map<String, String> sdkConfig = new HashMap<>();
        sdkConfig.put("mode", MODE_CONFIG);
        return sdkConfig;
    }

    private static Payment createPayment(String accessToken, String description, int money) {
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
        redirectUrls.setCancelUrl(CANCEL_URL);
        redirectUrls.setReturnUrl(RETURN_URL);
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
