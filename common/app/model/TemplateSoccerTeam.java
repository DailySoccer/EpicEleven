package model;


import com.mongodb.WriteConcern;
import org.bson.types.ObjectId;
import org.jongo.marshall.jackson.oid.Id;
import model.opta.*;

import java.util.Date;

public class TemplateSoccerTeam implements JongoId {
    @Id
    public ObjectId templateSoccerTeamId;

    public String optaTeamId;

    public String name;
    public String shortName;

    public Date createdAt;

    // Constructor por defecto (necesario para Jongo: "unmarshall result to class")
    public TemplateSoccerTeam() {
    }

    public TemplateSoccerTeam(OptaTeam optaTeam) {
        optaTeamId = optaTeam.optaTeamId;
        name = optaTeam.name;
        shortName = optaTeam.shortName;
        createdAt = GlobalDate.getCurrentDate();
    }

    public ObjectId getId() {
        return templateSoccerTeamId;
    }

    static public TemplateSoccerTeam find(ObjectId templateSoccerTeamId) {
        return Model.templateSoccerTeams().findOne("{_id : #}", templateSoccerTeamId).as(TemplateSoccerTeam.class);
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