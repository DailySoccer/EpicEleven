package model;

import com.paypal.core.*;

import java.net.URL;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.Map;

public class PaypalIPNMessage {
    public static final String FIELD_RECEIVER_EMAIL = "receiver_email";
    public static final String FIELD_PAYMENT_STATUS = "payment_status";
    public static final String FIELD_TRANSACTION_ID = "txn_id";
    public static final String FIELD_CUSTOM_ID = "custom";

    public static final String PAYMENT_STATUS_COMPLETED = "Completed";
    public static final String PAYMENT_STATUS_DENIED = "Denied";
    public static final String PAYMENT_STATUS_FAILED = "Failed";

    private static final String ENCODING = "windows-1252";

    private Map<String, String> ipnMap = new HashMap<>();
    private Map<String, String> configurationMap = null;
    private HttpConfiguration httpConfiguration = null;
    private String ipnEndpoint = Constants.EMPTY_STRING;
    private boolean isIpnVerified = false;
    private StringBuffer payload;

    /**
     * Populates HttpConfiguration with connection specifics parameters
     */
    private void initialize() {
        httpConfiguration = new HttpConfiguration();
        ipnEndpoint = getIPNEndpoint();
        httpConfiguration.setEndPointUrl(ipnEndpoint);
        httpConfiguration.setConnectionTimeout(Integer.parseInt(configurationMap.get(Constants.HTTP_CONNECTION_TIMEOUT)));
        httpConfiguration.setMaxRetry(Integer.parseInt(configurationMap.get(Constants.HTTP_CONNECTION_RETRY)));
        httpConfiguration.setReadTimeout(Integer.parseInt(configurationMap.get(Constants.HTTP_CONNECTION_READ_TIMEOUT)));
        httpConfiguration.setMaxHttpConnection(Integer.parseInt(configurationMap.get(Constants.HTTP_CONNECTION_MAX_CONNECTION)));
    }

    /**
     * Constructs {@link PaypalIPNMessage} using the given {@link Map} for name and
     * values.
     *
     * @param ipnMap
     *            {@link Map} representing IPN name/value pair
     */
    public PaypalIPNMessage(Map<String, String> ipnMap) {
        this(ipnMap, ConfigManager.getInstance().getConfigurationMap());
    }

    /**
     * Constructs {@link PaypalIPNMessage} using the given {@link Map} for name and
     * values and a custom configuration {@link Map}
     *
     * @param ipnMap
     *            {@link Map} representing IPN name/value pair
     * @param configurationMap
     */
    public PaypalIPNMessage(Map<String, String> ipnMap, Map<String, String> configurationMap) {
        this.configurationMap = SDKUtil.combineDefaultMap(configurationMap);
        initialize();
        payload = new StringBuffer("cmd=_notify-validate");
        if (ipnMap != null) {
            String encodingParam = ipnMap.get("charset");
            String encoding = encodingParam != null ? encodingParam : ENCODING;
            for (Map.Entry<String, String> entry : ipnMap.entrySet()) {
                String name = entry.getKey();
                String value = entry.getValue();
                try {
                    this.ipnMap.put(name, URLDecoder.decode(value, encoding));
                    payload.append("&").append(name).append("=")
                            .append(URLEncoder.encode(value, encoding));
                } catch (Exception e) {
                    LoggingManager.debug(PaypalIPNMessage.class, e.getMessage());
                }
            }
        }
    }

    /**
     * This method post back ipn payload to PayPal system for verification
     */
    public boolean validate() {
        Map<String, String> headerMap = new HashMap<>();
        URL url = null;
        String res = Constants.EMPTY_STRING;
        HttpConnection connection = ConnectionManager.getInstance().getConnection();

        try {

            connection.createAndconfigureHttpConnection(httpConfiguration);
            url = new URL(this.ipnEndpoint);
            headerMap.put("Host", url.getHost());
            res = Constants.EMPTY_STRING;
            if (!this.isIpnVerified) {
                res = connection.execute(null, payload.toString(), headerMap);
            }

        } catch (Exception e) {
            LoggingManager.debug(PaypalIPNMessage.class, e.getMessage());
        }

        // check notification validation
        if (res.equals("VERIFIED")) {
            isIpnVerified = true;
        }

        return isIpnVerified;
    }

    /**
     * @return Map of IPN name/value parameters
     */
    public Map<String, String> getIpnMap() {
        return ipnMap;
    }

    /**
     * @param ipnName
     * @return IPN value for corresponding IpnName
     */
    public String getIpnValue(String ipnName) {
        return ipnMap.get(ipnName);
    }

    /**
     * @return Transaction Type (eg: express_checkout, cart, web_accept)
     */
    public String getTransactionType() {
        return ipnMap.containsKey("txn_type")   ? ipnMap.get("txn_type")
                                                : ipnMap.get("transaction_type");
    }

    public boolean isPaymentStatusCompleted() {
        return PAYMENT_STATUS_COMPLETED.equalsIgnoreCase(ipnMap.get(FIELD_PAYMENT_STATUS));
    }

    public boolean isPaymentStatusFailed() {
        String paymentStatus = ipnMap.get(FIELD_PAYMENT_STATUS);
        return  PAYMENT_STATUS_DENIED.equalsIgnoreCase(paymentStatus) ||
                PAYMENT_STATUS_FAILED.equalsIgnoreCase(paymentStatus);
    }

    private String getIPNEndpoint() {
        String ipnEPoint = configurationMap.get(Constants.IPN_ENDPOINT);
        if (ipnEPoint == null) {
            String mode = configurationMap.get(Constants.MODE);
            if (mode != null) {
                if (Constants.SANDBOX.equalsIgnoreCase(mode.trim())) {
                    ipnEPoint = Constants.IPN_SANDBOX_ENDPOINT;
                } else if (Constants.LIVE.equalsIgnoreCase(mode.trim())) {
                    ipnEPoint = Constants.IPN_LIVE_ENDPOINT;
                }
            }
        }
        return ipnEPoint;
    }

}

