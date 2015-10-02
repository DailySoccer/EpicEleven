package controllers.admin;

import model.*;
import org.bson.types.ObjectId;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import play.data.format.Formats;
import play.data.validation.Constraints;
import play.data.validation.ValidationError;

import java.math.BigDecimal;
import java.util.*;

public class TemplateContestForm {

    public String id;

    public ContestState state;

    // Por defecto, el formulario asumirá "Virtual" (dada su mayor frecuencia de creación)
    public TypeContest typeContest = TypeContest.VIRTUAL;

    @Constraints.Required
    public String name;             // Auto-gen if blank

    @Constraints.Min(1)
    @Constraints.Required
    public int minInstances = 1;        // Minimum desired number of instances that we want running at any given moment

    @Constraints.Required
    public int maxEntries = 10;

    @Constraints.Required
    public SalaryCap salaryCap = SalaryCap.STANDARD;
    @Constraints.Required
    public BigDecimal entryFee = new BigDecimal(1);
    @Constraints.Required
    public float prizeMultiplier = 1.0f;
    @Constraints.Required
    public PrizeType prizeType = PrizeType.WINNER_TAKES_ALL;

    @Constraints.Required
    public List<String> templateMatchEvents = new ArrayList<>();  // We rather have it here that normalize it in a N:N table

    @Formats.DateTime (pattern = "yyyy-MM-dd'T'HH:mm")
    public Date activationAt;

    @Formats.DateTime (pattern = "yyyy-MM-dd'T'HH:mm")
    public java.util.Date startDate;

    // Fecha expresada en Time (para que sea más fácil volverla a convertir en Date; se usa para filtrar por fecha)
    public long createdAt;

    public TemplateContestForm() {
        state = ContestState.DRAFT;
        activationAt = GlobalDate.getCurrentDate();
        createdAt = GlobalDate.getCurrentDate().getTime();
    }

    public TemplateContestForm(TemplateContest templateContest) {
        id = templateContest.templateContestId.toString();
        state = templateContest.state;
        typeContest = templateContest.simulation ? TypeContest.VIRTUAL : TypeContest.REAL;
        name = templateContest.name;
        minInstances = templateContest.minInstances;
        maxEntries = templateContest.maxEntries;
        salaryCap = SalaryCap.findByMoney(templateContest.salaryCap);
        entryFee = templateContest.entryFee.getAmount();
        prizeMultiplier = templateContest.prizeMultiplier;
        prizeType = templateContest.prizeType;

        for(TemplateMatchEvent matchEvent : TemplateMatchEvent.findAll(templateContest.templateMatchEventIds)) {
            templateMatchEvents.add(matchEvent.templateMatchEventId.toString());
        }

        activationAt = templateContest.activationAt;
        startDate = templateContest.startDate;
        createdAt = templateContest.createdAt.getTime();
    }

    public static HashMap<String, String> matchEventsOptions(long startTime) {
        return matchEventsOptions(new Date(startTime));
    }

    public static HashMap<String, String> matchEventsOptions(Date startDate) {
        HashMap<String, String> options = new LinkedHashMap<>();

        final int MAX_MATCH_EVENTS = 100;
        List<TemplateMatchEvent> templateMatchEventsResults = utils.ListUtils.asList(Model.templateMatchEvents()
                .find("{startDate: {$gte: #, $lte: #}, simulation: {$ne: true}}", startDate, new DateTime(startDate).plusDays(20).toDate())
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
