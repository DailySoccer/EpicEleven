package controllers;

import actions.AllowCors;
import actions.UserAuthenticated;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import model.User;
import model.accounting.AccountOp;
import model.accounting.AccountingTran;
import model.accounting.AccountingTranBonus;
import model.accounting.AccountingTranOrder;
import model.bonus.SignupBonus;
import model.shop.Catalog;
import model.shop.Order;
import model.shop.Product;
import org.bson.types.ObjectId;
import org.joda.money.CurrencyUnit;
import org.joda.money.Money;
import play.Logger;
import play.mvc.Controller;
import play.mvc.Result;
import utils.MoneyUtils;
import utils.ReturnHelper;

import java.util.ArrayList;
import java.util.List;

@AllowCors.Origin
public class ShopController extends Controller {

    static final String REFERER_URL_DEFAULT = "epiceleven.com";

    public static Result getCatalog() {
        return new ReturnHelper(ImmutableMap.of("products", Catalog.Products)).toResult();
    }

    @UserAuthenticated
    public static Result buyProduct(String productId) {
        User theUser = (User)ctx().args.get("User");

        Logger.debug("buyProduct: {} user: {}", productId, theUser.userId.toString());

        // Obtenemos desde qué url están haciendo la solicitud
        String refererUrl = request().hasHeader("Referer") ? request().getHeader("Referer") : REFERER_URL_DEFAULT;

        Product product = Catalog.findOne(productId);

        // Crear el identificador del nuevo pedido
        ObjectId orderId = new ObjectId();

        Order order = Order.create(orderId, theUser.userId, Order.TransactionType.IN_GAME, "payment_ID_generic", product, refererUrl);

        // Registramos la operación de PAGO del pedido
        if (product.price.getCurrencyUnit().equals(CurrencyUnit.EUR) ||
            product.price.getCurrencyUnit().equals(MoneyUtils.CURRENCY_GOLD) ||
            product.price.getCurrencyUnit().equals(MoneyUtils.CURRENCY_MANAGER)) {
            AccountingTranOrder.create(product.price.getCurrencyUnit().getCode(), orderId, productId, ImmutableList.of(
                    new AccountOp(theUser.userId, product.price, User.getSeqId(theUser.userId) + 1)
            ));
        }

        // Registramos la operación de RECEPCIÓN del pedido
        if (product.gained.getCurrencyUnit().equals(MoneyUtils.CURRENCY_GOLD) ||
            product.gained.getCurrencyUnit().equals(MoneyUtils.CURRENCY_MANAGER)) {
            AccountingTranOrder.create(product.gained.getCurrencyUnit().getCode(), orderId, productId, ImmutableList.of(
                    new AccountOp(theUser.userId, product.gained, User.getSeqId(theUser.userId) + 1)
            ));
        }
        else if (product.gained.getCurrencyUnit().equals(MoneyUtils.CURRENCY_ENERGY)) {
            Logger.debug("addEnergy: {}", product.gained.toString());

            // Si lo que ha comprado es Energía, le damos Energía
            theUser.addEnergy(product.gained);
        }

        order.setCompleted();

        return new ReturnHelper(ImmutableMap.of("profile", theUser.getProfile())).toResult();
    }

    @UserAuthenticated
    public static Result buySoccerPlayer(String contestId, String templateSoccerPlayerId) {
        User theUser = (User)ctx().args.get("User");
        return new ReturnHelper(ImmutableMap.of("profile", theUser.getProfile())).toResult();
    }
}
