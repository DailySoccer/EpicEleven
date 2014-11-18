package model;

import com.paypal.api.payments.*;
import com.paypal.core.rest.APIContext;
import com.paypal.core.rest.OAuthTokenCredential;
import com.paypal.core.rest.PayPalRESTException;
import org.bson.types.ObjectId;
import java.util.*;

public class PaypalPayment {

    static final String MODE_SANDBOX = "sandbox";
    static final String MODE_LIVE = "live";
    static final String MODE_CONFIG = MODE_SANDBOX;

    static final String HOSTNAME_DEFAULT = "backend.epiceleven.com";

    // Las rutas relativas al SERVER a las que enviaremos las respuestas proporcionadas por Paypal
    static final String SERVER_CANCEL_PATH = "/paypal/execute_payment?cancel=true&orderId=";
    static final String SERVER_RETURN_PATH = "/paypal/execute_payment?success=true&orderId=";

    /*
        LIVE CONFIGURATION
     */
    static final String LIVE_CLIENT_ID = "AXGKyxAeNjwaGg4gNwHDEoidWC7_uQeRgaFAWTccuLqb1-R-s11FWbceSWR0";
    static final String LIVE_SECRET = "ENlBYxDHZVn_hotpxYtCXD3NPvvPQSmj8CbfzYWZyaFddkQTwhhw3GxV5Ipe";
    static final String LIVE_CANCEL_URL = "http://backend.epiceleven.com" + SERVER_CANCEL_PATH;
    static final String LIVE_RETURN_URL = "http://backend.epiceleven.com" + SERVER_RETURN_PATH;

    /*
        SANDBOX CONFIGURATION
     */
    static final String SANDBOX_CLIENT_ID = "AXGKyxAeNjwaGg4gNwHDEoidWC7_uQeRgaFAWTccuLqb1-R-s11FWbceSWR0";
    static final String SANDBOX_SECRET = "ENlBYxDHZVn_hotpxYtCXD3NPvvPQSmj8CbfzYWZyaFddkQTwhhw3GxV5Ipe";
    static final String SANDBOX_CANCEL_URL = "https://devtools-paypal.com/guide/pay_paypal?cancel=true&orderId=";
    static final String SANDBOX_RETURN_URL = "https://devtools-paypal.com/guide/pay_paypal?success=true&orderId=";

    static final String CURRENCY = "EUR";

    static final String TRANSACTION_DESCRIPTION = "Creating a payment";

    // Identificador de un Link enviado por Paypal para proceder a la "aprobación" del pago por parte del pagador
    static final String LINK_APPROVAL_URL = "approval_url";

    public static PaypalPayment instance() {
        if (_instance == null) {
            _instance = new PaypalPayment();
        }
        return _instance;
    }

    public void setHostName(String hostName) {
        _hostName = hostName;
    }

    /**
     *
     * @param orderId       Identificador del pedido. Se incluirá en la url de respuesta de Paypal para reconocer a qué pedido hace referencia
     * @param product       Producto que se quiere comprar
     * @return Respuesta dada por Paypal (podría ser "aprobada", "quedarse pendiente" o "cancelada")
     */
    public Payment createPayment(ObjectId orderId, Product product) throws PayPalRESTException {
        // Moneda usada y dinero
        Amount amount = new Amount();
        amount.setCurrency(CURRENCY);
        amount.setTotal(String.valueOf(product.price));

        // Crear la lista de productos
        List<Item> items = new ArrayList<>();
        Item item = new Item();
        item.setQuantity(String.valueOf(1));
        item.setName(product.name);
        item.setPrice(String.valueOf(product.price));
        item.setCurrency(CURRENCY);
        items.add(item);

        ItemList itemList = new ItemList();
        itemList.setItems(items);

        // Descripción (127 caracteres max.) y Cantidad solicitada
        Transaction transaction = new Transaction();
        transaction.setDescription(TRANSACTION_DESCRIPTION);
        transaction.setAmount(amount);
        transaction.setItemList(itemList);

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
        payment.setRedirectUrls(getRedirectUrls(orderId));

        // Solicitud de "aprobación" a Paypal
        return payment.create(getAPIContext());
    }

    public Payment completePayment(String paymentId, String payerId) throws PayPalRESTException {
        // Proporcionamos el identificador del pago
        Payment payment = new Payment();
        payment.setId(paymentId);

        // Proporcionar el id del "pagador" (proporcionado en el "return_url" por Paypal)
        PaymentExecution paymentExecute = new PaymentExecution();
        paymentExecute.setPayerId(payerId);

        // Procedemos a solicitar a Paypal que lleve a cabo el pago, para que el dinero pase de una cuenta a otra
        return payment.execute(getAPIContext(), paymentExecute);
    }

    public Payment verifyPayment(String paymentId) throws PayPalRESTException {
        return Payment.get(getAPIContext(), paymentId);
    }

    public APIContext getAPIContext() throws PayPalRESTException {
        APIContext apiContext = new APIContext(getAccessToken());
        apiContext.setConfigurationMap(getSdkConfig());
        return apiContext;
    }

    public Map<String, String> getSdkConfig() {
        if (_sdkConfig == null) {
            _sdkConfig = new HashMap<>();
            _sdkConfig.put("mode", MODE_CONFIG);
        }
        return _sdkConfig;
    }

    public String getApprovalURL(Payment payment) {
        String redirectUrl = null;
        List<Links> links = payment.getLinks();
        for (Links link : links) {
            if (link.getRel().equalsIgnoreCase(LINK_APPROVAL_URL)) {
                redirectUrl = link.getHref();
                break;
            }
        }
        return redirectUrl;
    }

    public RedirectUrls getRedirectUrls(ObjectId orderId) {
        // Incluir en las urls el identificador del pedido
        RedirectUrls redirectUrls = new RedirectUrls();
        if (isLive()) {
            redirectUrls.setCancelUrl(LIVE_CANCEL_URL + orderId.toString());
            redirectUrls.setReturnUrl(LIVE_RETURN_URL + orderId.toString());
        }
        else {
            redirectUrls.setCancelUrl("http://" + _hostName + SERVER_CANCEL_PATH + orderId.toString());
            redirectUrls.setReturnUrl("http://" + _hostName + SERVER_RETURN_PATH + orderId.toString());
        }
        return redirectUrls;
    }

    // ###AccessToken
    // Retrieve the access token from OAuthTokenCredential by passing in ClientID and ClientSecret
    // It is not mandatory to generate Access Token on a per call basis.
    // Typically the access token can be generated once and reused within the expiry window
    public String getAccessToken() throws PayPalRESTException {
        if (_accessToken == null) {
            _accessToken = isLive()
                    ? new OAuthTokenCredential(LIVE_CLIENT_ID, LIVE_SECRET, getSdkConfig()).getAccessToken()
                    : new OAuthTokenCredential(SANDBOX_CLIENT_ID, SANDBOX_SECRET, getSdkConfig()).getAccessToken();

            /*
            // ClientID and ClientSecret retrieved from configuration
            String clientID = ConfigManager.getInstance().getValue("clientID");
            String clientSecret = ConfigManager.getInstance().getValue("clientSecret");
            _accessToken = new OAuthTokenCredential(clientID, clientSecret).getAccessToken();
            */
        }
        return _accessToken;
    }

    public boolean isLive() {
        return getSdkConfig().get("mode").equals(MODE_LIVE);
    }

    static PaypalPayment _instance;
    Map<String, String> _sdkConfig;
    String _accessToken;
    String _hostName = HOSTNAME_DEFAULT;
}
