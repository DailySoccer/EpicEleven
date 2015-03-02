package controllers;

import actions.AllowCors;
import actions.UserAuthenticated;
import com.google.common.collect.ImmutableMap;
import model.Promo;
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
    public static Result getPromos() {
        return new ReturnHelper(ImmutableMap.of("promos", Promo.getCurrent())).toResult();
    }
}
