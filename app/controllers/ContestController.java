package controllers;

import actions.AllowCors;
import model.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import play.Logger;
import play.data.Form;
import play.data.validation.Constraints;
import play.mvc.Controller;
import play.mvc.Result;
import utils.*;

import java.util.Date;
import java.util.HashMap;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import com.mongodb.MongoException;

import static play.data.Form.form;

import org.bson.types.ObjectId;
import java.util.List;
import java.util.ArrayList;
import java.util.Iterator;

//@UserAuthenticated
@AllowCors.Origin
public class ContestController extends Controller {

    public static Result getActiveContests() {
        // User theUser = (User)ctx().args.get("User");

        long startTime = System.currentTimeMillis();

        Date startDate = new DateTime(2014, 10, 14, 12, 0, DateTimeZone.UTC).toDate();

        HashMap<String, Object> contest = new HashMap<>();

        contest.put("match_events", Model.templateMatchEvents().find("{startDate: #}", startDate).as(TemplateMatchEvent.class));
        contest.put("template_contests", Model.templateContests().find("{startDate: #}", startDate).as(TemplateContest.class));
        contest.put("contests", Model.contests().find().as(Contest.class));

        // Logger.info("getActiveContests: {}", System.currentTimeMillis() - startTime);

        return new ReturnHelper(contest).toResult();
    }

    public static Result getActiveMatchEvents() {
        // User theUser = (User)ctx().args.get("User");

        Date startDate = new DateTime(2014, 10, 14, 12, 0, DateTimeZone.UTC).toDate();
        return new ReturnHelper(Model.templateMatchEvents().find(
            //"{startDate: {$lte : #}}", startDate
            "{startDate: #}", startDate
        ).as(TemplateMatchEvent.class)).toResult();
    }

    // https://github.com/playframework/playframework/tree/master/samples/java/forms
    public static class ContestEntryParams {
        @Constraints.Required
        public String userId;

        @Constraints.Required
        public String contestId;

        @Constraints.Required
        public String soccerTeam;
    }

    public static Result addContestEntry() {
        Form<ContestEntryParams> contestEntryForm = form(ContestEntryParams.class).bindFromRequest();

        if (!contestEntryForm.hasErrors()) {
            ContestEntryParams params = contestEntryForm.get();

            Logger.info("addFantasyTeam: userId({}) contestId({}) soccerTeam({})", params.userId, params.contestId, params.soccerTeam);

            // Obtener el userId : ObjectId
            User aUser = Model.findUserId(params.userId);
            if (aUser == null) {
                contestEntryForm.reject("userId", "User invalid");
            }

            // Obtener el contestId : ObjectId
            Contest aContest = Model.findContestId(params.contestId);
            if (aContest == null) {
                contestEntryForm.reject("contestId", "Contest invalid");
            }

            // Obtener los soccerIds : List<ObjectId>
            List<ObjectId> soccerIds = new ArrayList<>();
            List<String> strIdsList = ListUtils.stringListFromString(",", params.soccerTeam);

            // Convertir las strings en ObjectId
            List<ObjectId> idsList = new ArrayList<>();
            for (String strId: strIdsList) {
                idsList.add( new ObjectId(strId) );
            }
            Iterable<TemplateSoccerPlayer> soccers = Model.findTemplateSoccerPlayersFromIds("_id", idsList);

            String soccerNames = "";
            for (TemplateSoccerPlayer soccer : soccers) {
                soccerNames += soccer.name + " / ";
                soccerIds.add(soccer.templateSoccerPlayerId);
            }

            if (!contestEntryForm.hasErrors()) {
                Logger.info("contestEntry: Contest[{}] / User[{}] = ({}) => {}", aContest.name, aUser.nickName, soccerIds.size(), soccerNames);

                // Crear el equipo en mongoDb.contestEntryCollection
                createContestEntry(new ObjectId(params.userId), new ObjectId(params.contestId), soccerIds);
            }
        }

        JsonNode result = contestEntryForm.errorsAsJson();

        if (!contestEntryForm.hasErrors()) {
            result = new ObjectMapper().createObjectNode().put("result", "ok");
        }
        return new ReturnHelper(!contestEntryForm.hasErrors(), result).toResult();
    }

    private static boolean createContestEntry(ObjectId user, ObjectId contest, List<ObjectId> soccers) {
        boolean bRet = true;

        try {
            ContestEntry aContestEntry = new ContestEntry(user, contest, soccers);
            Model.contestEntries().insert(aContestEntry);
        } catch (MongoException exc) {
            Logger.error("createContestEntry: ", exc);
            bRet = false;
        }

        return bRet;
    }

    public static Result getLiveMatchEventsFromTemplateContest(String templateContestId) {
        Logger.info("getLiveMatchEventsFromTemplateContest: {}", templateContestId);

        long startTime = System.currentTimeMillis();

        if (!ObjectId.isValid(templateContestId)) {
            return new ReturnHelper(false, "TemplateContest invalid").toResult();
        }

        // Obtenemos el TemplateContest
        TemplateContest templateContest = Model.templateContests().findOne("{ _id: # }",
                new ObjectId(templateContestId)).as(TemplateContest.class);

        if (templateContest == null) {
            return new ReturnHelper(false, "TemplateContest not found").toResult();
        }

        Iterable<LiveMatchEvent> liveMatchEventResults = Model.findLiveMatchEventsFromIds("templateMatchEventId", templateContest.templateMatchEventIds);
        List<LiveMatchEvent> liveMatchEventList = ListUtils.listFromIterator(liveMatchEventResults.iterator());

        // TODO: Si no encontramos ningun LiveMatchEvent, los creamos
        if (liveMatchEventList.size() == 0) {
            Logger.info("create liveMatchEvents from TemplateContest({})", templateContest.templateContestId);

            // Obtenemos la lista de TemplateMatchEvents correspondientes al TemplateContest
            Iterable<TemplateMatchEvent> templateMatchEventsResults = Model.findTemplateMatchEventFromIds("_id", templateContest.templateMatchEventIds);

            // Creamos un LiveMatchEvent correspondiente a un TemplateMatchEvent
            for (TemplateMatchEvent templateMatchEvent : templateMatchEventsResults) {
                LiveMatchEvent liveMatchEvent = new LiveMatchEvent(templateMatchEvent);
                // Lo insertamos en la BDD
                Model.liveMatchEvents().insert(liveMatchEvent);
                // Lo añadimos en la lista de elementos a devolver
                liveMatchEventList.add(liveMatchEvent);
            }
        }
        Logger.info("END: getLiveMatchEventsFromTemplateContest: {}", System.currentTimeMillis() - startTime);

        return new ReturnHelper(liveMatchEventList).toResult();
    }

    public static Result getLiveMatchEventsFromMatchEvents(String strMatchEventsId) {
        Logger.info("getLiveMatchEventsFromMatchEvents: {}", strMatchEventsId);

        long startTime = System.currentTimeMillis();

        // Separamos la cadena de MatchEventIds (separados por ',') en una lista de StringsIds
        List<String> strMatchEventIdsList = ListUtils.stringListFromString(",", strMatchEventsId);

        // Convertir las strings en ObjectId
        List<ObjectId> idsList = new ArrayList<>();
        for (String strId: strMatchEventIdsList) {
            idsList.add( new ObjectId(strId) );
        }

        Iterable<LiveMatchEvent> liveMatchEventResults = Model.findLiveMatchEventsFromIds("templateMatchEventId", idsList);
        List<LiveMatchEvent> liveMatchEventList = ListUtils.listFromIterator(liveMatchEventResults.iterator());

        Logger.info("END: getLiveMatchEventsFromMatchEvents: {}", System.currentTimeMillis() - startTime);

        return new ReturnHelper(liveMatchEventList).toResult();
    }

    public static Result getLiveContestEntries(String contest) {
        Logger.info("getLiveContestEntries: {}", contest);

        if (!ObjectId.isValid(contest)) {
            return new ReturnHelper(false, "Contest invalid").toResult();
        }

        ObjectId contestId = new ObjectId(contest);
        return new ReturnHelper(Model.contestEntries().find("{contestId: #}", contestId).as(ContestEntry.class)).toResult();
    }

    public static Result setLiveFantasyPointsOfSoccerPlayer(String strSoccerPlayerId, String strPoints) {
        if (!ObjectId.isValid(strSoccerPlayerId)) {
            return new ReturnHelper(false, "SoccerPlayer invalid").toResult();
        }

        Model.setLiveFantasyPointsOfSoccerPlayer(new ObjectId(strSoccerPlayerId), strPoints);

        return ok();
    }

    public static Result updateLiveFantasyPoints(String strMatchEventsId) {
        // Separamos la cadena de MatchEventIds (separados por ',') en una lista de StringsIds
        List<String> strMatchEventIdsList = ListUtils.stringListFromString(",", strMatchEventsId);

        // Convertir las strings en ObjectId
        List<ObjectId> idsList = new ArrayList<>();
        for (String strId: strMatchEventIdsList) {
            idsList.add( new ObjectId(strId) );
        }

        Model.updateLiveFantasyPoints(idsList);

        return ok();
    }
}
