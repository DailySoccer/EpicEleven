package controllers;

import actions.AllowCors;
import actions.UserAuthenticated;
import com.google.common.collect.ImmutableMap;
import model.Promo;
import play.cache.Cached;
import play.mvc.Controller;
import play.mvc.Result;
import play.mvc.With;
import utils.ReturnHelper;

import java.util.ArrayList;

@AllowCors.Origin
public class PromoController extends Controller {
    private final static int CACHE_PROMO = 12 * 60 * 60;            // 12 horas

    private static final String ERROR_VIEW_PROMO_INVALID = "ERROR_VIEW_PROMO_INVALID";
    private static final String ERROR_MY_PROMO_INVALID = "ERROR_MY_PROMO_INVALID";

    /*
     * Devuelve la lista de promos activas
     */
    public static Result getPromo(String codeName) {
        return new ReturnHelper(ImmutableMap.of("promos", Promo.findByCodeName(codeName))).toResult();
    }

    @With(AllowCors.CorsAction.class)
    @Cached(key = "Promos", duration = CACHE_PROMO)
    public static Result getPromos() {
        // TODO No hay promos en esta versión
        return new ReturnHelper(ImmutableMap.of("promos", new ArrayList<Promo>() /*Promo.getCurrent()*/)).toResult();
    }

}
