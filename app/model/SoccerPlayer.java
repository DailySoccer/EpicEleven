package model;

public class SoccerPlayer {
    public String name;
    public FieldPos fieldPos;
    public int salary;
    public int fantasyPoints;

    // Constructor por defecto (necesario para Jongo: "unmarshall result to class")
    public SoccerPlayer() {
    }

    public SoccerPlayer(TemplateSoccerPlayer template) {
        name = template.name;
        fieldPos = template.fieldPos;
        salary = template.salary;
        fantasyPoints = template.fantasyPoints;
    }
}
