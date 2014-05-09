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

@AllowCors.Origin
@UserAuthenticated
public class ContestController extends Controller {

    public static Result getActiveContestsPack() {

        User theUser = (User)ctx().args.get("User");

        ContestsPack contestsPack = new ContestsPack();

        contestsPack.contests = Model.contests().find().as(Contest.class);
        contestsPack.matchEvents = Model.matchEvents().find().as(MatchEvent.class);

        return new ReturnHelper(contestsPack).toResult();
    }

    private static class ContestsPack {
        public Iterable<Contest> contests;
        public Iterable<MatchEvent> matchEvents;
    }
}
