package model;

import org.bson.types.ObjectId;

public class SoccerPlayer {
    public ObjectId templateSoccerPlayerId;
    public String optaPlayerId;
    public String name;
    public FieldPos fieldPos;
    public int salary;
    public int fantasyPoints;

    // Constructor por defecto (necesario para Jongo: "unmarshall result to class")
    public SoccerPlayer() {
    }

    public SoccerPlayer(TemplateSoccerPlayer template) {
        templateSoccerPlayerId = template.templateSoccerPlayerId;
        optaPlayerId = template.optaPlayerId;
        name = template.name;
        fieldPos = template.fieldPos;
        salary = template.salary;
        fantasyPoints = template.fantasyPoints;
    }
}
