package model;


import org.bson.types.ObjectId;
import org.jongo.marshall.jackson.oid.Id;

public class TemplateSoccerPlayer {
    @Id
    public ObjectId templateSoccerPlayerId;

    public String name;
    public FieldPos fieldPos;
    public int salary;
    public int fantasyPoints;

    public ObjectId templateTeamId;
}
