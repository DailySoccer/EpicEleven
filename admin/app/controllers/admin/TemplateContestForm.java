package controllers.admin;

import model.*;
import play.data.validation.Constraints;
import play.data.validation.ValidationError;

import java.text.DateFormat;
import java.util.*;

// https://github.com/playframework/playframework/tree/master/samples/java/forms
public class TemplateContestForm {
    public String id;

    @Constraints.Required
    public TemplateContest.State state;

    @Constraints.Required
    public String name;             // Auto-gen if blank

    @Constraints.Required
    public String postName;         // This goes in parenthesis

    @Constraints.Required
    public int minInstances;        // Minimum desired number of instances that we want running at any given moment

    @Constraints.Required
    public int maxEntries;

    @Constraints.Required
    public int salaryCap;
    @Constraints.Required
    public int entryFee;
    @Constraints.Required
    public PrizeType prizeType;

    @Constraints.Required
    public List<String> templateMatchEvents = new ArrayList<>();  // We rather have it here that normalize it in a N:N table

    public Date startDate = new Date();

    public TemplateContestForm() {
    }

    public TemplateContestForm(TemplateContest templateContest) {
        id = templateContest.templateContestId.toString();
        state = templateContest.state;
        name = templateContest.name;
        postName = templateContest.postName;
        minInstances = templateContest.minInstances;
        maxEntries = templateContest.maxEntries;
        salaryCap = templateContest.salaryCap;
        entryFee = templateContest.entryFee;
        prizeType = templateContest.prizeType;

        Iterable<TemplateMatchEvent> templateMatchEventsResults = TemplateMatchEvent.find("_id", templateContest.templateMatchEventIds);
        for(TemplateMatchEvent matchEvent : templateMatchEventsResults) {
            templateMatchEvents.add(matchEvent.optaMatchEventId);
        }

        startDate = templateContest.startDate;
    }

    public static HashMap<String, String> matchEventsOptions() {
        HashMap<String, String> options = new LinkedHashMap<>();
        Iterable<TemplateMatchEvent> templateMatchEventsResults = Model.templateMatchEvents().find().sort("{startDate : 1}").as(TemplateMatchEvent.class);
        for (TemplateMatchEvent matchEvent: templateMatchEventsResults) {
            options.put(matchEvent.optaMatchEventId, String.format("%s - %s vs %s",
                    // new SimpleDateFormat("yy/MM/dd").format(matchEvent.startDate),
                    DateFormat.getDateInstance(DateFormat.SHORT).format(matchEvent.startDate),
                    matchEvent.soccerTeamA.name, matchEvent.soccerTeamB.name));
        }
        return options;
    }

    public List<ValidationError> validate() {

        List<ValidationError> errors = new ArrayList<>();

        if(errors.size() > 0)
            return errors;

        return null;
    }
}
