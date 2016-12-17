package controllers.admin;

import com.google.common.collect.ImmutableMap;
import com.mongodb.WriteResult;
import model.*;
import play.mvc.Controller;
import play.mvc.Result;

import java.util.List;
import java.util.Map;

public class MaintenanceController extends Controller {

    private static final String queryVirtual = "{ simulation : true }";
    private static final String queryJobs = "{ state : {$in: [\"DONE\", \"CANCELED\"]} }";

    public static Result index() {
        long numVirtualTemplateContests = Model.templateContests().count(queryVirtual);
        long numVirtualContests = Model.contests().count(queryVirtual);
        long numVirtualTemplateMatchEvents = Model.templateMatchEvents().count(queryVirtual);
        long numJobsDoneOrCanceled = Model.jobs().count(queryJobs);

        StringBuffer buffer = new StringBuffer();

        buffer.append("<h5>VirtualTemplateContests: " + String.valueOf(numVirtualTemplateContests) + "</h5>");
        buffer.append("<h5>VirtualContests: " + String.valueOf(numVirtualContests) + "</h5>");
        buffer.append("<h5>VirtualTemplateMatchEvents: " + String.valueOf(numVirtualTemplateMatchEvents) + "</h5>");
        buffer.append("<h5>JobsDoneOrCanceled: " + String.valueOf(numJobsDoneOrCanceled) + "</h5>");

        FlashMessage.info(buffer.toString());

        return ok(views.html.maintenance.render(ImmutableMap.of(
                "numVirtualTemplateContests", numVirtualTemplateContests,
                "numVirtualContests", numVirtualContests,
                "numVirtualTemplateMatchEvents", numVirtualTemplateMatchEvents
        )));
    }

    public static Result apply() {
        WriteResult resultTemplateContests = Model.templateContests().remove(queryVirtual);
        WriteResult resultContests = Model.contests().remove(queryVirtual);
        WriteResult resultTemplateMatchEvents = Model.templateMatchEvents().remove(queryVirtual);
        WriteResult resultJobs = Model.jobs().remove(queryJobs);

        StringBuffer buffer = new StringBuffer();

        buffer.append("<h5>VirtualTemplateContests: " + String.valueOf(resultTemplateContests.getN()) + " deleted</h5>");
        buffer.append("<h5>VirtualContests: " + String.valueOf(resultContests.getN()) + " deleted</h5>");
        buffer.append("<h5>VirtualTemplateMatchEvents: " + String.valueOf(resultTemplateMatchEvents.getN()) + " deleted</h5>");
        buffer.append("<h5>JobsDoneOrCanceled: " + String.valueOf(resultJobs.getN()) + " deleted</h5>");

        FlashMessage.success(buffer.toString());

        return index();
    }
}