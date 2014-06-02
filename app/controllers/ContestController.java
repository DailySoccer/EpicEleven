package controllers;

import actions.AllowCors;
import actions.UserAuthenticated;
import model.*;
import org.joda.time.format.ISODateTimeFormat;
import play.Logger;
import play.mvc.Controller;
import play.mvc.Result;
import utils.ReturnHelper;

import java.util.Date;
import java.util.HashMap;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import com.mongodb.BasicDBObject;

//@UserAuthenticated
@AllowCors.Origin
public class ContestController extends Controller {

    public static Result getActiveContests() {
        User theUser = (User)ctx().args.get("User");

        Date startDate = new DateTime(2014, 10, 14, 12, 0, DateTimeZone.UTC).toDate();

        HashMap<String, Object> contest = new HashMap<String,Object>();
        contest.put("match_events", Model.templateMatchEvents().find("{startDate: #}", startDate).as(TemplateMatchEvent.class));
        contest.put("template_contests", Model.templateContests().find("{startDate: #}", startDate).as(TemplateContest.class));
        contest.put("contests", Model.contests().find().as(Contest.class));
        return new ReturnHelper(contest).toResult();
    }

    public static Result getActiveMatchEvents() {
        User theUser = (User)ctx().args.get("User");

        Date startDate = new DateTime(2014, 10, 14, 12, 0, DateTimeZone.UTC).toDate();
        return new ReturnHelper(Model.templateMatchEvents().find(
            //"{startDate: {$lte : #}}", new DateTime(2014, 10, 14, 12, 0, DateTimeZone.UTC).toDate()
            "{startDate: #}", startDate
        ).as(TemplateMatchEvent.class)).toResult();
    }
}
