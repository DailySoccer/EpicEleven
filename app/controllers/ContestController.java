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
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import com.mongodb.BasicDBObject;

//@UserAuthenticated
@AllowCors.Origin
public class ContestController extends Controller {

    public static Result getActiveContests() {
        User theUser = (User)ctx().args.get("User");

        Date startDate = new DateTime(2014, 10, 14, 12, 0, DateTimeZone.UTC).toDate();

        ReturnHelper ret = new ReturnHelper();
        ret.include("match_events", Model.templateMatchEvents().find("{startDate: #}", startDate).as(TemplateMatchEvent.class));
        ret.include("template_contests", Model.templateContests().find("{startDate: #}", startDate).as(TemplateContest.class));
        ret.include("contests", Model.contests().find().as(Contest.class));
        return ret.toResult();
        // return new ReturnHelper(Model.contests().find().as(Contest.class)).toResult();
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
