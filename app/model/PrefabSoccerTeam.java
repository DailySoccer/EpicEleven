package model;


import org.bson.types.ObjectId;
import org.jongo.marshall.jackson.oid.Id;

public class PrefabSoccerTeam {
    @Id
    public ObjectId prefabSoccerTeamId;

    public String name;
}