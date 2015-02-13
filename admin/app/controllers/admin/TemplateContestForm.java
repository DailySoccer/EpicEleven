package controllers.admin;

import model.*;
import org.bson.types.ObjectId;
import org.joda.money.Money;
import org.joda.time.DateTime;
import play.data.validation.Constraints;
import play.data.validation.ValidationError;

import java.math.BigDecimal;
import java.util.*;

public class TemplateContestForm {
    public String id;

    public ContestState state;

    @Constraints.Required
    public String name;             // Auto-gen if blank

    @Constraints.Required
    public int minInstances;        // Minimum desired number of instances that we want running at any given moment

    @Constraints.Required
    public int maxEntries;

    @Constraints.Required
    public int salaryCap;
    @Constraints.Required
    public BigDecimal entryFee;
    @Constraints.Required
    public PrizeType prizeType;

    @Constraints.Required
    public List<String> templateMatchEvents = new ArrayList<>();  // We rather have it here that normalize it in a N:N table

    public Date activationAt;

    // Fecha expresada en Time (para que sea más fácil volverla a convertir en Date; se usa para filtrar por fecha)
    public long createdAt;

    public TemplateContestForm() {
        state = ContestState.OFF;
        activationAt = GlobalDate.getCurrentDate();
        createdAt = GlobalDate.getCurrentDate().getTime();
    }

    public TemplateContestForm(TemplateContest templateContest) {
        id = templateContest.templateContestId.toString();
        state = templateContest.state;
        name = templateContest.name;
        minInstances = templateContest.minInstances;
        maxEntries = templateContest.maxEntries;
        salaryCap = templateContest.salaryCap;
        entryFee = templateContest.entryFee.getAmount();
        prizeType = templateContest.prizeType;

        for(TemplateMatchEvent matchEvent : TemplateMatchEvent.findAll(templateContest.templateMatchEventIds)) {
            templateMatchEvents.add(matchEvent.templateMatchEventId.toString());
        }

        activationAt = templateContest.activationAt;
        createdAt = templateContest.createdAt.getTime();
    }

    public static HashMap<String, String> matchEventsOptions(long startTime) {
        return matchEventsOptions(new Date(startTime));
    }

    public static HashMap<String, String> matchEventsOptions(Date startDate) {
        HashMap<String, String> options = new LinkedHashMap<>();

        final int MAX_MATCH_EVENTS = 100;
        List<TemplateMatchEvent> templateMatchEventsResults = utils.ListUtils.asList(Model.templateMatchEvents()
                .find("{startDate: {$gte: #, $lte: #}}", startDate, new DateTime(startDate).plusDays(20).toDate())
                .sort("{startDate : 1}").limit(MAX_MATCH_EVENTS).as(TemplateMatchEvent.class));

        List<ObjectId> teamIds = new ArrayList<>();
        for (TemplateMatchEvent matchEvent: templateMatchEventsResults) {
            teamIds.add(matchEvent.templateSoccerTeamAId);
            teamIds.add(matchEvent.templateSoccerTeamBId);
        }
        Map<ObjectId, TemplateSoccerTeam> teams = TemplateSoccerTeam.findAllAsMap(teamIds);

        for (TemplateMatchEvent matchEvent: templateMatchEventsResults) {
            TemplateSoccerTeam teamA = teams.get(matchEvent.templateSoccerTeamAId);
            TemplateSoccerTeam teamB = teams.get(matchEvent.templateSoccerTeamBId);
            options.put(matchEvent.templateMatchEventId.toString(), String.format("%s - %s vs %s",
                        GlobalDate.formatDate(matchEvent.startDate),
                        teamA.name, teamB.name));
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
