package model;

import org.bson.types.ObjectId;

public class InstanceSoccerPlayer {
    public ObjectId templateSoccerPlayerId;

    public ObjectId templateSoccerTeamId;

    public FieldPos fieldPos;

    public int salary;

    public InstanceSoccerPlayer() { }

    public InstanceSoccerPlayer(TemplateSoccerPlayer template) {
        templateSoccerPlayerId = template.templateSoccerPlayerId;
        templateSoccerTeamId = template.templateTeamId;
        fieldPos = template.fieldPos;
        salary = template.salary;
    }
}
