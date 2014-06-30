package model;


import com.mongodb.WriteConcern;
import org.bson.types.ObjectId;
import org.jongo.marshall.jackson.oid.Id;
import model.opta.*;

public class TemplateSoccerTeam {
    @Id
    public ObjectId templateSoccerTeamId;

    public String optaTeamId;

    public String name;
    public String shortName;

    // Constructor por defecto (necesario para Jongo: "unmarshall result to class")
    public TemplateSoccerTeam() {}

    public TemplateSoccerTeam(OptaTeam optaTeam) {
        optaTeamId = optaTeam.id;
        name = optaTeam.name;
        shortName = optaTeam.shortName;
    }

    static public TemplateSoccerTeam find(ObjectId templateSoccerTeamId) {
        return Model.templateSoccerTeams().findOne("{_id : #}", templateSoccerTeamId).as(TemplateSoccerTeam.class);
    }

    public boolean isEqual(OptaTeam optaTeam) {
        return optaTeamId.equals(optaTeam.id) &&
               name.equals(optaTeam.name) &&
               shortName.equals(optaTeam.shortName);
    }

    /**
     * Importar un optaTeam
     * @param optaTeam
     * @return
     */
    static public boolean importTeam(OptaTeam optaTeam) {
        TemplateSoccerTeam templateTeam = new TemplateSoccerTeam(optaTeam);
        Model.templateSoccerTeams().withWriteConcern(WriteConcern.SAFE).insert(templateTeam);

        Model.optaTeams().update("{id: #}", optaTeam.id).with("{$set: {dirty: false}}");
        return true;
    }

    static public boolean isInvalid(OptaTeam optaTeam) {
        return (optaTeam.name == null || optaTeam.name.isEmpty() || optaTeam.shortName == null || optaTeam.shortName.isEmpty());
    }
}