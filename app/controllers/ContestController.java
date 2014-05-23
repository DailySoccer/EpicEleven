package controllers;

import actions.AllowCors;
import actions.UserAuthenticated;
import model.Contest;
import model.MatchEvent;
import model.Model;
import model.User;
import play.mvc.Controller;
import play.mvc.Result;
import utils.ReturnHelper;

import java.util.ArrayList;

@AllowCors.Origin
@UserAuthenticated
public class ContestController extends Controller {

    public static Result getActiveContests() {
        User theUser = (User)ctx().args.get("User");

        return new ReturnHelper(Model.contests().find().as(Contest.class)).toResult();
    }

    public static Result getActiveMatchEvents() {
        User theUser = (User)ctx().args.get("User");

        return new ReturnHelper(Model.matchEvents().find().as(MatchEvent.class)).toResult();
    }
}