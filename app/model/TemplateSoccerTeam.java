package model;


import org.bson.types.ObjectId;
import org.jongo.marshall.jackson.oid.Id;

public class TemplateSoccerTeam {
    @Id
    public ObjectId templateSoccerTeamId;

    public String name;
}