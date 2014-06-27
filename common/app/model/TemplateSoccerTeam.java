package model;


import org.bson.types.ObjectId;
import org.jongo.marshall.jackson.oid.Id;
import model.opta.*;

public class TemplateSoccerTeam {
    @Id
    public ObjectId templateSoccerTeamId;

    public String optaTeamId;

    public String name;

    // Constructor por defecto (necesario para Jongo: "unmarshall result to class")
    public TemplateSoccerTeam() {}

    public TemplateSoccerTeam(OptaTeam optaTeam) {
        optaTeamId = optaTeam.id;
        name = optaTeam.name;
    }

    public boolean isEqual(OptaTeam optaTeam) {
        return optaTeamId.equals(optaTeam.id) &&
               name.equals(optaTeam.name);
    }
}