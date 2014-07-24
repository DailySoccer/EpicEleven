package model;


import com.mongodb.WriteConcern;
import org.bson.types.ObjectId;
import org.jongo.marshall.jackson.oid.Id;
import model.opta.*;
import utils.ListUtils;

import java.util.*;

public class TemplateSoccerTeam implements JongoId, Initializer {
    @Id
    public ObjectId templateSoccerTeamId;

    public String optaTeamId;

    public String name;
    public String shortName;

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

    static public List<TemplateSoccerTeam> findAll() {
        return ListUtils.asList(Model.templateSoccerTeams().find().as(TemplateSoccerTeam.class));
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
        Model.templateSoccerTeams().withWriteConcern(WriteConcern.SAFE).insert(templateTeam);

        Model.optaTeams().update("{id: #}", optaTeam.optaTeamId).with("{$set: {dirty: false}}");
        return true;
    }

    static public boolean isInvalid(OptaTeam optaTeam) {
        return (optaTeam.name == null || optaTeam.name.isEmpty() || optaTeam.shortName == null || optaTeam.shortName.isEmpty());
    }
}