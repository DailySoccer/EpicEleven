package model;

import org.bson.types.ObjectId;

public class SoccerPlayer {
    public ObjectId templateSoccerPlayerId;
    public String optaPlayerId;
    public String name;
    public FieldPos fieldPos;
    public int salary;
    public int playedMatches;
    public int fantasyPoints;

    public SoccerPlayer() { }

    public SoccerPlayer(TemplateSoccerPlayer template) {
        templateSoccerPlayerId = template.templateSoccerPlayerId;
        optaPlayerId = template.optaPlayerId;
        name = template.name;
        fieldPos = template.fieldPos;
        salary = template.salary;
        playedMatches = template.stats.size();
        fantasyPoints = template.fantasyPoints;
    }
}
