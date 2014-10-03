package model;


import com.fasterxml.jackson.annotation.JsonView;
import com.mongodb.WriteConcern;
import org.bson.types.ObjectId;
import org.jongo.marshall.jackson.oid.Id;
import model.opta.*;
import utils.ListUtils;

import java.util.*;

public class TemplateSoccerTeam implements JongoId, Initializer {
    @Id
    public ObjectId templateSoccerTeamId;

    @JsonView(JsonViews.NotForClient.class)
    public String optaTeamId;

    public String name;
    public String shortName;

    @JsonView(JsonViews.NotForClient.class)
    public Date createdAt;

    public TemplateSoccerTeam() { }

    public TemplateSoccerTeam(OptaTeam optaTeam) {
        optaTeamId = optaTeam.optaTeamId;
        name = optaTeam.name;
        shortName = optaTeam.shortName;
        createdAt = GlobalDate.getCurrentDate();
    }

    public void Initialize() { }

    public ObjectId getId() {
        return templateSoccerTeamId;
    }

    public List<TemplateSoccerPlayer> getTemplateSoccerPlayers() {
        return ListUtils.asList(Model.templateSoccerPlayers().find("{ templateTeamId: # }", templateSoccerTeamId).as(TemplateSoccerPlayer.class));
    }

    static public TemplateSoccerTeam findOne(ObjectId templateSoccerTeamId) {
        return Model.templateSoccerTeams().findOne("{_id : #}", templateSoccerTeamId).as(TemplateSoccerTeam.class);
    }

    static public TemplateSoccerTeam findOneFromOptaId(String optaTeamId) {
        return Model.templateSoccerTeams().findOne("{optaTeamId: #}", optaTeamId).as(TemplateSoccerTeam.class);
    }

    static public TemplateSoccerTeam findOne(ObjectId templateSoccerTeamId, String optaTeamId) {
        return Model.templateSoccerTeams().findOne("{_id: #, optaTeamId: #}", templateSoccerTeamId, optaTeamId).as(TemplateSoccerTeam.class);
    }

    static public List<TemplateSoccerTeam> findAll() {
        return ListUtils.asList(Model.templateSoccerTeams().find().as(TemplateSoccerTeam.class));
    }

    static public List<TemplateSoccerTeam> findAll(List<ObjectId> templateSoccerTeamIds) {
        return ListUtils.asList(Model.findObjectIds(Model.templateSoccerTeams(), "_id", templateSoccerTeamIds).as(TemplateSoccerTeam.class));
    }

    static public List<TemplateSoccerTeam> findAllFromMatchEvents(List<TemplateMatchEvent> matchEvents) {
        List<ObjectId> teamIds = new ArrayList<>();
        for (TemplateMatchEvent matchEvent: matchEvents) {
            teamIds.add(matchEvent.templateSoccerTeamAId);
            teamIds.add(matchEvent.templateSoccerTeamBId);
        }
        return findAll(teamIds);
    }

    static public HashMap<ObjectId, TemplateSoccerTeam> findAllAsMap(){
        HashMap<ObjectId, TemplateSoccerTeam> map = new HashMap<>();
        for (TemplateSoccerTeam templateSoccerTeam: findAll()) {
            map.put(templateSoccerTeam.getId(), templateSoccerTeam);
        }
        return map;
    }

    public boolean hasChanged(OptaTeam optaTeam) {
        return !optaTeamId.equals(optaTeam.optaTeamId) ||
               !name.equals(optaTeam.name) ||
               !shortName.equals(optaTeam.shortName);
    }

    /**
     * Importar un optaTeam
     * @param optaTeam
     * @return
     */
    static public boolean importTeam(OptaTeam optaTeam) {
        TemplateSoccerTeam templateTeam = new TemplateSoccerTeam(optaTeam);
        Model.templateSoccerTeams().withWriteConcern(WriteConcern.SAFE).update("{optaTeamId: #}", templateTeam.optaTeamId).upsert().with(templateTeam);

        Model.optaTeams().update("{id: #}", optaTeam.optaTeamId).with("{$set: {dirty: false}}");
        return true;
    }

    static public boolean isInvalid(OptaTeam optaTeam) {
        return (optaTeam.name == null || optaTeam.name.isEmpty() || optaTeam.shortName == null || optaTeam.shortName.isEmpty());
    }

    static public void createInvalidTeam() {
        OptaTeam.createInvalidTeam();

        TemplateSoccerTeam invalidTeam = new TemplateSoccerTeam();
        invalidTeam.optaTeamId = OptaTeam.INVALID_TEAM;
        invalidTeam.name = "-Unknown-";
        invalidTeam.shortName = "XXX";
        invalidTeam.createdAt = GlobalDate.getCurrentDate();
        Model.templateSoccerTeams().insert(invalidTeam);
    }
}