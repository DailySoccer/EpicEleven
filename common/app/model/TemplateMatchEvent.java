package model;


import org.bson.types.ObjectId;
import org.jongo.Find;
import org.jongo.marshall.jackson.oid.Id;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import model.opta.*;
import play.Logger;

public class TemplateMatchEvent {
    @Id
    public ObjectId templateMatchEventId;

    public String optaMatchEventId;
    public String optaCompetitionId;
    public String optaSeasonId;

    public SoccerTeam soccerTeamA;
    public SoccerTeam soccerTeamB;

    public Date startDate;
    public Date createdAt;

    public TemplateMatchEvent() {
    }

    static public TemplateMatchEvent find(ObjectId templateMatchEventId) {
        return Model.templateMatchEvents().findOne("{_id : #}", templateMatchEventId).as(TemplateMatchEvent.class);
    }

    public boolean hasChanged(OptaMatchEvent optaMatchEvent) {
        return !optaMatchEventId.equals(optaMatchEvent.optaMatchEventId) ||
               !soccerTeamA.optaTeamId.equals(optaMatchEvent.homeTeamId) ||
               !soccerTeamB.optaTeamId.equals(optaMatchEvent.awayTeamId) ||
               !startDate.equals(optaMatchEvent.matchDate);
    }

    /**
     *  Query de la lista de Template Match Events correspondientes a una lista de template contests
     */
    static public Find find(List<TemplateContest> templateContests) {
        List<ObjectId> templateContestObjectIds = new ArrayList<>(templateContests.size());
        for (TemplateContest templateContest: templateContests) {
            templateContestObjectIds.addAll(templateContest.templateMatchEventIds);
        }
        return Model.findObjectIds(Model.templateMatchEvents(), "_id", templateContestObjectIds);
    }

    public static Iterable<TemplateMatchEvent> find(String fieldId, List<ObjectId> idList) {
        return Model.findObjectIds(Model.templateMatchEvents(), fieldId, idList).as(TemplateMatchEvent.class);
    }

    /**
     *  Estado del partido
     */
    public boolean isStarted() {
        String optaId = Model.getMatchEventIdFromOpta(optaMatchEventId);

        // Inicio del partido?
        OptaEvent optaEvent = Model.optaEvents().findOne("{gameId: #, typeId: 32, periodId: 1}", optaId).as(OptaEvent.class);
        if (optaEvent == null) {
            // Kick Off Pass?
            optaEvent = Model.optaEvents().findOne("{gameId: #, typeId: 1, periodId: 1, qualifiers: 278}", optaId).as(OptaEvent.class);
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
        String optaId = Model.getMatchEventIdFromOpta(optaMatchEventId);

        OptaEvent optaEvent = Model.optaEvents().findOne("{gameId: #, typeId: 30, periodId: 14}", optaId).as(OptaEvent.class);

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

    /**
     * Crea un template match event
     * @param teamA     TeamA
     * @param teamB     TeamB
     * @param startDate Cuando se jugara el partido
     * @return El template match event creado
     */
    static public TemplateMatchEvent create(TemplateSoccerTeam teamA, TemplateSoccerTeam teamB, Date startDate) {
        return create(null, teamA, teamB, startDate);
    }

    static public TemplateMatchEvent create(OptaMatchEvent optaMatchEvent, TemplateSoccerTeam teamA, TemplateSoccerTeam teamB, Date startDate) {
        Logger.info("Template MatchEvent: {} vs {} ({})", teamA.name, teamB.name, startDate);

        TemplateMatchEvent templateMatchEvent = new TemplateMatchEvent();
        templateMatchEvent.startDate = startDate;
        templateMatchEvent.optaMatchEventId = optaMatchEvent.optaMatchEventId;
        templateMatchEvent.optaCompetitionId = optaMatchEvent.competitionId;
        templateMatchEvent.optaSeasonId = optaMatchEvent.seasonId;
        templateMatchEvent.soccerTeamA = SoccerTeam.create(templateMatchEvent, teamA);
        templateMatchEvent.soccerTeamB = SoccerTeam.create(templateMatchEvent, teamB);
        templateMatchEvent.createdAt = GlobalDate.getCurrentDate();

        // TODO: Eliminar condicion (optaMatchEventId == null)
        if (optaMatchEvent != null) {
            // Insertar o actualizar
            Model.templateMatchEvents().update("{optaMatchEventId: #}", optaMatchEvent.optaMatchEventId).upsert().with(templateMatchEvent);
        }
        else {
            Model.templateMatchEvents().insert(templateMatchEvent);
        }

        return templateMatchEvent;
    }

    /**
     * Importar un optaMatchEvent
     * @param optaMatchEvent
     * @return
     */
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

    static public boolean isInvalid(OptaMatchEvent optaMatchEvent) {
        boolean invalid = (optaMatchEvent.homeTeamId == null) || optaMatchEvent.homeTeamId.isEmpty() || (optaMatchEvent.awayTeamId == null) || optaMatchEvent.awayTeamId.isEmpty();

        if (!invalid) {
            TemplateSoccerTeam teamA = Model.templateSoccerTeams().findOne("{optaTeamId: #}", optaMatchEvent.homeTeamId).as(TemplateSoccerTeam.class);
            TemplateSoccerTeam teamB = Model.templateSoccerTeams().findOne("{optaTeamId: #}", optaMatchEvent.awayTeamId).as(TemplateSoccerTeam.class);
            invalid = (teamA == null) || (teamB == null);
        }

        return invalid;
    }
}
