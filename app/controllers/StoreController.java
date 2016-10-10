package controllers;

import actions.AllowCors;
import actions.UserAuthenticated;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import jdk.nashorn.internal.ir.annotations.Immutable;
import model.Model;
import model.User;
import model.jobs.CompleteOrderJob;
import model.shop.Catalog;
import model.shop.Order;
import model.shop.ProductMoney;
import org.bson.types.ObjectId;
import play.Play;
import play.Logger;
import play.libs.F;
import play.libs.Json;
import play.libs.ws.WS;
import play.libs.ws.WSRequestHolder;
import play.libs.ws.WSResponse;

import play.data.Form;
import play.data.validation.Constraints;
import play.mvc.Controller;
import play.mvc.Result;
import utils.ReturnHelper;

import java.security.*;
import javax.crypto.*;

import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

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

    public static Result validator() {
        play.data.DynamicForm requestData = form().bindFromRequest();
        //Logger.debug("{}", requestData);
        Logger.debug("Hola Mundo");

        if( requestData.get("transaction.type").equals("android-playstore") ) {
            Logger.debug("ANDROID");
            // Validacion Android
            String dataStr = requestData.get("transaction.receipt");
            byte[] data = dataStr.getBytes();

            String signatureStr = requestData.get("transaction.signature");
            byte[] signature = Base64.getDecoder().decode(signatureStr);

            String publicKeyStr = Play.application().configuration().getString("market_app_key_android");
            byte[] publicKey = Base64.getDecoder().decode(publicKeyStr);

            try {
                KeyFactory kf = KeyFactory.getInstance("RSA");
                PublicKey pubKey = kf.generatePublic(new X509EncodedKeySpec(publicKey));
                Signature sign = Signature.getInstance("SHA1withRSA");
                sign.initVerify(pubKey);
                sign.update(data);
                if(sign.verify(signature)) return new ReturnHelper(true, ImmutableMap.of(  "ok", true, "data", ImmutableMap.of(  "code", 0, "msg", "Ok") ) ).toResult();

            } catch (Exception e) {
                Logger.debug("{}", e);

            }

        }else{
            Logger.debug("IOS");
            try {
                JsonNode node = StoreController.post(Play.application().configuration().getString("market_verification_url_ios"), Json.newObject().put("receipt-data", requestData.get("transaction.receipt")));
                if( node.get("status").asInt()==0)
                    return new ReturnHelper(true, ImmutableMap.of(  "ok", true, "data", ImmutableMap.of(  "code", 0, "msg", "Ok") ) ).toResult();
            }catch(Exception e){
                Logger.debug("{}", e);
            }
        }
        return new ReturnHelper(true, ImmutableMap.of(  "ok", false, "data", ImmutableMap.of(  "code", 1, "msg", "Error in validation") ) ).toResult();

    }

    private static JsonNode post(String url, JsonNode jsonNode) throws TimeoutException {
        WSRequestHolder requestHolder = WS.url(url);

        F.Promise<WSResponse> response = requestHolder
                .setHeader("content-type", "application/x-www-form-urlencoded")
                .post(jsonNode);

        F.Promise<JsonNode> jsonPromise = response.map(
                new F.Function<WSResponse, JsonNode>() {
                    public JsonNode apply(WSResponse response) {
                        try {
                            return response.asJson();
                        }
                        catch (Exception exc) {
                            Logger.debug("Json incorrecto: {}", response.getStatusText());
                            return JsonNodeFactory.instance.objectNode();
                        }
                    }
                }
        );

        return jsonPromise.get(5000, TimeUnit.MILLISECONDS);
    }

}



