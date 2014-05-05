package controllers;


import actions.CorsComposition;
import model.User;
import play.mvc.Controller;
import play.mvc.Result;
import utils.SessionUtils;

@CorsComposition.Cors
public class ContestController extends Controller {

    public static Result getActiveContests() {
        User theUser = SessionUtils.getUserFromRequest(request());




        return null;
    }
}
