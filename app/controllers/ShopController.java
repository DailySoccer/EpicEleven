package controllers;

import actions.AllowCors;
import com.google.common.collect.ImmutableMap;
import model.shop.Product;
import play.mvc.Controller;
import play.mvc.Result;
import utils.ReturnHelper;

@AllowCors.Origin
public class ShopController extends Controller {

    public static Result getCatalog() {
        return new ReturnHelper(ImmutableMap.of("products", Product.Catalog)).toResult();
    }
}
