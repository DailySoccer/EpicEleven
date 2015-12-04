package controllers.admin;

import model.*;
import org.bson.types.ObjectId;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import play.data.format.Formats;
import play.data.validation.Constraints;
import play.data.validation.ValidationError;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.*;

public class TemplateContestForm {
    public enum SelectionYESNO {
        NO(0),
        YES(1);

        public final int id;

        SelectionYESNO(int id) {
            this.id = id;
        }
    }

    public String id;

    public ContestState state;

    public String optaCompetitionId;

    // Por defecto, el formulario asumirá "Virtual" (dada su mayor frecuencia de creación)
    public TypeContest typeContest = TypeContest.VIRTUAL;
    public SelectionYESNO typeCustomizable;

    @Constraints.Required
    public String name;             // Auto-gen if blank

    @Constraints.Min(0)
    @Constraints.Required
    public int minInstances = 1;        // Minimum desired number of instances that we want running at any given moment

    @Constraints.Min(2)
    @Constraints.Required
    public int minEntries = 2;

    @Constraints.Required
    public int maxEntries = 10;

    @Constraints.Required
    public SalaryCap salaryCap = SalaryCap.STANDARD;
    @Constraints.Required
    public int entryFee = 1;
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

    public String specialImage;

    // Fecha expresada en Time (para que sea más fácil volverla a convertir en Date; se usa para filtrar por fecha)
    public long createdAt;

    public TemplateContestForm() {
        this.state = ContestState.DRAFT;
        this.activationAt = GlobalDate.getCurrentDate();
        this.startDate = activationAt;
        this.createdAt = GlobalDate.getCurrentDate().getTime();
    }

    public TemplateContestForm(TemplateContest templateContest) {
        id = templateContest.templateContestId != null ? templateContest.templateContestId.toString() : null;
        state = templateContest.state;
        optaCompetitionId = templateContest.optaCompetitionId;
        typeContest = templateContest.simulation ? TypeContest.VIRTUAL : TypeContest.REAL;
        typeCustomizable = templateContest.customizable ? SelectionYESNO.YES : SelectionYESNO.NO;
        name = templateContest.name;
        minInstances = templateContest.minInstances;
        minEntries = templateContest.minEntries;
        maxEntries = templateContest.maxEntries;
        salaryCap = SalaryCap.findByMoney(templateContest.salaryCap);
        entryFee = templateContest.entryFee.getAmount().intValue();
        prizeMultiplier = templateContest.prizeMultiplier;
        prizeType = templateContest.prizeType;

        for(TemplateMatchEvent matchEvent : TemplateMatchEvent.findAll(templateContest.templateMatchEventIds)) {
            templateMatchEvents.add(matchEvent.templateMatchEventId.toString());
        }

        activationAt = templateContest.activationAt;
        startDate = templateContest.startDate;

        specialImage = templateContest.specialImage;

        createdAt = templateContest.createdAt.getTime();
    }

    public static HashMap<String, String> matchEventsOptions(String optaCompetitionId, long startTime) {
        return matchEventsOptions(optaCompetitionId, new Date(startTime));
    }

    public static HashMap<String, String> matchEventsOptions(String optaCompetitionId, Date startDate) {
        /*
        final int MAX_MATCH_EVENTS = 100;
        List<TemplateMatchEvent> templateMatchEventsResults = utils.ListUtils.asList(Model.templateMatchEvents()
                .find("{startDate: {$gte: #, $lte: #}, simulation: {$ne: true}}", startDate, new DateTime(startDate).plusDays(20).toDate())
                .sort("{startDate : 1}").limit(MAX_MATCH_EVENTS).as(TemplateMatchEvent.class));
        */
        List<TemplateMatchEvent> templateMatchEventsResults = utils.ListUtils.asList(Model.templateMatchEvents()
                .find("{optaCompetitionId: #, startDate: {$gte: #}, simulation: {$ne: true}}", optaCompetitionId, startDate)
                .sort("{startDate : 1}").as(TemplateMatchEvent.class));

        return matchEventsOptions(templateMatchEventsResults);
    }

    public static HashMap<String, String> matchEventsOptions(List<TemplateMatchEvent> templateMatchEvents) {
        HashMap<String, String> options = new LinkedHashMap<>();

        List<ObjectId> teamIds = new ArrayList<>();
        for (TemplateMatchEvent matchEvent: templateMatchEvents) {
            teamIds.add(matchEvent.templateSoccerTeamAId);
            teamIds.add(matchEvent.templateSoccerTeamBId);
        }
        Map<ObjectId, TemplateSoccerTeam> teams = TemplateSoccerTeam.findAllAsMap(teamIds);

        for (TemplateMatchEvent matchEvent: templateMatchEvents) {
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
