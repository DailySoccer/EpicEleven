package model;

import com.fasterxml.jackson.annotation.JsonView;
import org.bson.types.ObjectId;

public class InstanceSoccerPlayer {
    public ObjectId templateSoccerPlayerId;

    public ObjectId templateTeamId;

    public FieldPos fieldPos;

    public int salary;

    public InstanceSoccerPlayer() { }

    public InstanceSoccerPlayer(TemplateSoccerPlayer template) {
        templateSoccerPlayerId = template.templateSoccerPlayerId;
        templateTeamId = template.templateTeamId;
        fieldPos = template.fieldPos;
        salary = template.salary;
    }
}
