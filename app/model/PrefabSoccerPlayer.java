package model;


import org.bson.types.ObjectId;
import org.jongo.marshall.jackson.oid.Id;

public class PrefabSoccerPlayer {
    @Id
    public ObjectId prefabSoccerPlayerId;

    public String name;
    public FieldPos fieldPos;
    public int salary;

    public ObjectId prefabTeamId;
}
