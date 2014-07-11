package model;


import model.opta.OptaEvent;
import model.opta.OptaMatchEvent;
import org.bson.types.ObjectId;
import org.jongo.Find;
import org.jongo.marshall.jackson.oid.Id;
import play.Logger;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import model.opta.*;
import play.Logger;

public class TemplateMatchEvent implements JongoId {
    @Id
    public ObjectId templateMatchEventId;

    public String optaMatchEventId;
    public String optaCompetitionId;
    public String optaSeasonId;

    public SoccerTeam soccerTeamA;
    public SoccerTeam soccerTeamB;

    public Date startDate;
    public Date createdAt;

    public TemplateMatchEvent() { }

    public ObjectId getId() {
        return templateMatchEventId;
    }

    public boolean hasChanged(OptaMatchEvent optaMatchEvent) {
        return !optaMatchEventId.equals(optaMatchEvent.optaMatchEventId) ||
               !soccerTeamA.optaTeamId.equals(optaMatchEvent.homeTeamId) ||
               !soccerTeamB.optaTeamId.equals(optaMatchEvent.awayTeamId) ||
               !startDate.equals(optaMatchEvent.matchDate);
    }

    static public TemplateMatchEvent find(ObjectId templateMatchEventId) {
        return Model.templateMatchEvents().findOne("{_id : #}", templateMatchEventId).as(TemplateMatchEvent.class);
    }

    /**
     *  Query de la lista de Template Match Events correspondientes a una lista de template contests
     */
    static public Find find(List<TemplateContest> templateContests) {
        List<ObjectId> templateMatchEventObjectIds = new ArrayList<>(templateContests.size());
        for (TemplateContest templateContest: templateContests) {
            templateMatchEventObjectIds.addAll(templateContest.templateMatchEventIds);
        }
        return Model.findObjectIds(Model.templateMatchEvents(), "_id", templateMatchEventObjectIds);
    }

    public static Iterable<TemplateMatchEvent> find(String fieldId, List<ObjectId> idList) {
        return Model.findObjectIds(Model.templateMatchEvents(), fieldId, idList).as(TemplateMatchEvent.class);
    }

    /**
     *  Estado del partido
     */
    public boolean isStarted() {
        // Inicio del partido?
        OptaEvent optaEvent = Model.optaEvents().findOne("{gameId: #, typeId: 32, periodId: 1}", optaMatchEventId).as(OptaEvent.class);
        if (optaEvent == null) {
            // Kick Off Pass?
            optaEvent = Model.optaEvents().findOne("{gameId: #, typeId: 1, periodId: 1, qualifiers: 278}", optaMatchEventId).as(OptaEvent.class);
        }

        /*
        Logger.info("isStarted? {}({}) = {}",
                find.soccerTeamA.name + " vs " + find.soccerTeamB.name, find.optaMatchEventId, (optaEvent!= null));
        */
        return (optaEvent != null);
    }

    public static boolean isStarted(String templateMatchEventId) {
        TemplateMatchEvent templateMatch = find(new ObjectId(templateMatchEventId));
        return templateMatch.isStarted();
    }

    public boolean isFinished() {
        OptaEvent optaEvent = Model.optaEvents().findOne("{gameId: #, typeId: 30, periodId: 14}", optaMatchEventId).as(OptaEvent.class);

        /*
        Logger.info("isFinished? {}({}) = {}",
                find.soccerTeamA.name + " vs " + find.soccerTeamB.name, find.optaMatchEventId, (optaEvent!= null));
        */
        return (optaEvent != null);
    }

    public static boolean isFinished(String templateMatchEventId) {
        TemplateMatchEvent templateMatch = find(new ObjectId(templateMatchEventId));
        return templateMatch.isFinished();
    }


    static public boolean importMatchEvent(OptaMatchEvent optaMatchEvent) {
        TemplateSoccerTeam teamA = Model.templateSoccerTeams().findOne("{optaTeamId: #}", optaMatchEvent.homeTeamId).as(TemplateSoccerTeam.class);
        TemplateSoccerTeam teamB = Model.templateSoccerTeams().findOne("{optaTeamId: #}", optaMatchEvent.awayTeamId).as(TemplateSoccerTeam.class);

        if (teamA != null && teamB != null) {
            create(optaMatchEvent, teamA, teamB, optaMatchEvent.matchDate);

            Model.optaMatchEvents().update("{id: #}", optaMatchEvent.optaMatchEventId).with("{$set: {dirty: false}}");
        }
        else {
            Logger.error("Ignorando OptaMatchEvent: {} ({})", optaMatchEvent.optaMatchEventId, optaMatchEvent.matchDate);
            return false;
        }
        return true;
    }

    static private TemplateMatchEvent create(OptaMatchEvent optaMatchEvent, TemplateSoccerTeam teamA, TemplateSoccerTeam teamB, Date startDate) {
        Logger.info("Template MatchEvent: {} vs {} ({})", teamA.name, teamB.name, startDate);

        TemplateMatchEvent templateMatchEvent = new TemplateMatchEvent();
        templateMatchEvent.startDate = startDate;
        templateMatchEvent.optaMatchEventId = optaMatchEvent.optaMatchEventId;
        templateMatchEvent.optaCompetitionId = optaMatchEvent.competitionId;
        templateMatchEvent.optaSeasonId = optaMatchEvent.seasonId;
        templateMatchEvent.soccerTeamA = SoccerTeam.create(templateMatchEvent, teamA);
        templateMatchEvent.soccerTeamB = SoccerTeam.create(templateMatchEvent, teamB);
        templateMatchEvent.createdAt = GlobalDate.getCurrentDate();

        Model.templateMatchEvents().update("{optaMatchEventId: #}", optaMatchEvent.optaMatchEventId).upsert().with(templateMatchEvent);

        return templateMatchEvent;
    }

    static public boolean isInvalid(OptaMatchEvent optaMatchEvent) {
        boolean invalid = (optaMatchEvent.homeTeamId == null) || optaMatchEvent.homeTeamId.isEmpty() ||
                          (optaMatchEvent.awayTeamId == null) || optaMatchEvent.awayTeamId.isEmpty();

        if (!invalid) {
            TemplateSoccerTeam teamA = Model.templateSoccerTeams().findOne("{optaTeamId: #}", optaMatchEvent.homeTeamId).as(TemplateSoccerTeam.class);
            TemplateSoccerTeam teamB = Model.templateSoccerTeams().findOne("{optaTeamId: #}", optaMatchEvent.awayTeamId).as(TemplateSoccerTeam.class);
            invalid = (teamA == null) || (teamB == null);
        }

        return invalid;
    }
}
