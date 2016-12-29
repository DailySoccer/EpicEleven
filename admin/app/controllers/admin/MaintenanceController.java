package controllers.admin;

import com.google.common.collect.ImmutableMap;
import com.mongodb.WriteResult;
import model.*;
import org.bson.types.ObjectId;
import org.jongo.Aggregate;
import play.mvc.Controller;
import play.mvc.Result;
import utils.ListUtils;

import java.util.List;
import java.util.Map;

public class MaintenanceController extends Controller {

    private static final String queryVirtual = "{ simulation : true }";
    private static final String queryJobs = "{ state : {$in: [\"DONE\", \"CANCELED\"]} }";

    private static class AggregateResult {
        private ObjectId _id;
        int count;
    }

    public static Result index() {
        /*
        long numVirtualTemplateContests = Model.templateContests().count(queryVirtual);
        long numVirtualContests = Model.contests().count(queryVirtual);
        long numVirtualTemplateMatchEvents = Model.templateMatchEvents().count(queryVirtual);
        long numJobsDoneOrCanceled = Model.jobs().count(queryJobs);
        */

        List<AggregateResult> aggregateResults = ListUtils.asList(Model.users().aggregate("{$match: {}}")
                .and("{$unwind: \"$contestsRating\" }")
                .and("{ $group: { _id: null, count: { $sum: 1 } } }")
                .as(AggregateResult.class));
        long numUserContestsRating = aggregateResults.isEmpty() ? 0 : aggregateResults.get(0).count;

        StringBuffer buffer = new StringBuffer();

        /*
        buffer.append("<h5>VirtualTemplateContests: " + String.valueOf(numVirtualTemplateContests) + "</h5>");
        buffer.append("<h5>VirtualContests: " + String.valueOf(numVirtualContests) + "</h5>");
        buffer.append("<h5>VirtualTemplateMatchEvents: " + String.valueOf(numVirtualTemplateMatchEvents) + "</h5>");
        buffer.append("<h5>JobsDoneOrCanceled: " + String.valueOf(numJobsDoneOrCanceled) + "</h5>");
        */

        buffer.append("<h5>User.contestsRating: " + String.valueOf(numUserContestsRating) + "</h5>");

        FlashMessage.info(buffer.toString());

        return ok(views.html.maintenance.render(ImmutableMap.of(
        )));
    }

    public static Result apply() {
        /*
        WriteResult resultTemplateContests = Model.templateContests().remove(queryVirtual);
        WriteResult resultContests = Model.contests().remove(queryVirtual);
        WriteResult resultTemplateMatchEvents = Model.templateMatchEvents().remove(queryVirtual);
        WriteResult resultJobs = Model.jobs().remove(queryJobs);
        */
        WriteResult resultJobs = Model.users().update("{ contestsRating: {$exists: true} }").multi().with("{ $set: {contestsRating: []} }");

        StringBuffer buffer = new StringBuffer();

        /*
        buffer.append("<h5>VirtualTemplateContests: " + String.valueOf(resultTemplateContests.getN()) + " deleted</h5>");
        buffer.append("<h5>VirtualContests: " + String.valueOf(resultContests.getN()) + " deleted</h5>");
        buffer.append("<h5>VirtualTemplateMatchEvents: " + String.valueOf(resultTemplateMatchEvents.getN()) + " deleted</h5>");
        buffer.append("<h5>JobsDoneOrCanceled: " + String.valueOf(resultJobs.getN()) + " deleted</h5>");
        */

        FlashMessage.success(buffer.toString());

        return index();
    }
}