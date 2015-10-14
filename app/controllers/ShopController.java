package controllers;

import actions.AllowCors;
import actions.UserAuthenticated;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import model.Contest;
import model.InstanceSoccerPlayer;
import model.TemplateSoccerPlayer;
import model.User;
import model.accounting.AccountOp;
import model.accounting.AccountingTran;
import model.accounting.AccountingTranBonus;
import model.accounting.AccountingTranOrder;
import model.bonus.SignupBonus;
import model.shop.*;
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
    private static final String ENTRY_KEY_ERROR = "error";
    private static final String ERROR_USER_BALANCE_NEGATIVE = "ERROR_USER_BALANCE_NEGATIVE";

    static final String REFERER_URL_DEFAULT = "epiceleven.com";

    public static Result getCatalog() {
        return new ReturnHelper(ImmutableMap.of("products", Catalog.Products)).toResult();
    }

    @UserAuthenticated
    public static Result buyProduct(String productId) {
        User theUser = (User)ctx().args.get("User");

        List<String> errores = new ArrayList<>();

        Logger.debug("buyProduct: {} user: {}", productId, theUser.userId.toString());

        // Obtenemos desde qué url están haciendo la solicitud
        String refererUrl = request().hasHeader("Referer") ? request().getHeader("Referer") : REFERER_URL_DEFAULT;

        ProductMoney product = (ProductMoney) Catalog.findOne(productId);

        if (product.price.getCurrencyUnit().equals(MoneyUtils.CURRENCY_GOLD) ||
            product.price.getCurrencyUnit().equals(MoneyUtils.CURRENCY_MANAGER)) {
            if (!theUser.hasMoney(product.price)) {
                errores.add(ERROR_USER_BALANCE_NEGATIVE);
            }
        }

        if (errores.isEmpty()) {
            // Crear el identificador del nuevo pedido
            ObjectId orderId = new ObjectId();

            Order order = Order.create(orderId, theUser.userId, Order.TransactionType.IN_GAME, "payment_ID_generic", product, refererUrl);

            // --------
            // Registramos la operación de PAGO del pedido

            if (product.price.getCurrencyUnit().equals(CurrencyUnit.EUR) ||
                    product.price.getCurrencyUnit().equals(MoneyUtils.CURRENCY_GOLD) ||
                    product.price.getCurrencyUnit().equals(MoneyUtils.CURRENCY_MANAGER)) {
                AccountingTranOrder.create(product.price.getCurrencyUnit().getCode(), orderId, productId, ImmutableList.of(
                        new AccountOp(theUser.userId, product.price.negated(), User.getSeqId(theUser.userId) + 1)
                ));
            }

            // --------
            // Registramos la operación de RECEPCIÓN del pedido

            // Si ha comprado Gold o Manager Points, lo añadiremos creando una transacción
            if (product.gained.getCurrencyUnit().equals(MoneyUtils.CURRENCY_GOLD) ||
                product.gained.getCurrencyUnit().equals(MoneyUtils.CURRENCY_MANAGER)) {
                AccountingTranOrder.create(product.gained.getCurrencyUnit().getCode(), orderId, productId, ImmutableList.of(
                        new AccountOp(theUser.userId, product.gained, User.getSeqId(theUser.userId) + 1)
                ));
            } else if (product.gained.getCurrencyUnit().equals(MoneyUtils.CURRENCY_ENERGY)) {
                // Si lo que ha comprado es Energía, le damos Energía
                theUser.addEnergy(product.gained);
            }

            order.setCompleted();
        }

        if (!errores.isEmpty()) {
            return new ReturnHelper(false, ImmutableMap.of(ENTRY_KEY_ERROR, errores)).toResult();
        }
        return new ReturnHelper(ImmutableMap.of("profile", theUser.getProfile())).toResult();
    }

    @UserAuthenticated
    public static Result buySoccerPlayer(String contestId, String templateSoccerPlayerId) {
        User theUser = (User)ctx().args.get("User");

        List<String> errores = new ArrayList<>();

        Logger.debug("buySoccerPlayer: {} contestId: {} user: {}", templateSoccerPlayerId, contestId, theUser.userId.toString());

        // Obtenemos desde qué url están haciendo la solicitud
        String refererUrl = request().hasHeader("Referer") ? request().getHeader("Referer") : REFERER_URL_DEFAULT;

        Contest contest = Contest.findOne(new ObjectId(contestId));
        InstanceSoccerPlayer instanceSoccerPlayer = contest.getInstanceSoccerPlayer(new ObjectId(templateSoccerPlayerId));
        int levelSoccerPlayer = TemplateSoccerPlayer.levelFromSalary(instanceSoccerPlayer.salary);

        User userUpdated = theUser.getProfile();
        if (userUpdated.managerLevel < levelSoccerPlayer) {
            int goldNeeded = levelSoccerPlayer - (int) userUpdated.managerLevel;

            // Creamos el producto de comprar al futbolista
            ProductSoccerPlayer product = new ProductSoccerPlayer(Money.zero(MoneyUtils.CURRENCY_GOLD).plus(goldNeeded), new ObjectId(contestId), new ObjectId(templateSoccerPlayerId));

            // Averiguar si tiene dinero suficiente...
            if (!userUpdated.hasMoney(product.price)) {
                errores.add(ERROR_USER_BALANCE_NEGATIVE);
            }

            if (errores.isEmpty()) {
                // Crear el identificador del nuevo pedido
                ObjectId orderId = new ObjectId();

                Order order = Order.create(orderId, theUser.userId, Order.TransactionType.IN_GAME, "payment_ID_generic", product, refererUrl);

                AccountingTranOrder.create(product.price.getCurrencyUnit().getCode(), orderId, product.productId, ImmutableList.of(
                        new AccountOp(theUser.userId, product.price.negated(), User.getSeqId(theUser.userId) + 1)
                ));

                order.setCompleted();
            }

            if (!errores.isEmpty()) {
                return new ReturnHelper(false, ImmutableMap.of(ENTRY_KEY_ERROR, errores)).toResult();
            }
        }
        return new ReturnHelper(ImmutableMap.of("profile", theUser.getProfile())).toResult();
    }
}
