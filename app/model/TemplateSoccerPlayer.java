package model;


import org.bson.types.ObjectId;
import org.jongo.marshall.jackson.oid.Id;
import model.opta.*;

public class TemplateSoccerPlayer {
    @Id
    public ObjectId templateSoccerPlayerId;

    public String optaPlayerId;

    public String name;
    public FieldPos fieldPos;
    public int salary;
    public int fantasyPoints;

    public ObjectId templateTeamId;

    // Constructor por defecto (necesario para Jongo: "unmarshall result to class")
    public TemplateSoccerPlayer() {}

    public TemplateSoccerPlayer(OptaPlayer optaPlayer, ObjectId aTemplateTeamId) {
        optaPlayerId = optaPlayer.id;
        name = optaPlayer.name;
        fieldPos = getFieldPostFromOpta(optaPlayer.position);
        templateTeamId = aTemplateTeamId;
    }

    static FieldPos getFieldPostFromOpta (String optaPosition) {
        FieldPos optaFieldPos;
        if      (optaPosition.startsWith("G"))  optaFieldPos = FieldPos.GOALKEEPER;
        else if (optaPosition.startsWith("D"))  optaFieldPos = FieldPos.DEFENSE;
        else if (optaPosition.startsWith("M"))  optaFieldPos = FieldPos.MIDDLE;
        else                                    optaFieldPos = FieldPos.FORWARD;
        return optaFieldPos;
    }
}
