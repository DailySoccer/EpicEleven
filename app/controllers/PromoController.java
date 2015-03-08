package controllers;

import actions.AllowCors;
import com.google.common.collect.ImmutableMap;
import model.Promo;
import play.libs.F;
import play.mvc.Controller;
import play.mvc.Result;
import utils.ReturnHelper;

@AllowCors.Origin
public class PromoController extends Controller {

    private static final String ERROR_VIEW_PROMO_INVALID = "ERROR_VIEW_PROMO_INVALID";
    private static final String ERROR_MY_PROMO_INVALID = "ERROR_MY_PROMO_INVALID";

    /*
     * Devuelve la lista de promos activas
     */
    public static F.Promise<Result> getPromo(String codeName) {
        return F.Promise.promise(() -> new ReturnHelper(ImmutableMap.of("promos", Promo.findByCodeName(codeName)))).map((ReturnHelper i) -> i.toResult());
    }

    public static F.Promise<Result> getPromos() {
        return F.Promise.promise(() -> new ReturnHelper(ImmutableMap.of("promos", Promo.getCurrent()))).map((ReturnHelper i) -> i.toResult());
    }

}
