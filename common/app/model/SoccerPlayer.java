package model;

import com.fasterxml.jackson.annotation.JsonView;
import org.bson.types.ObjectId;

public class SoccerPlayer {
    public ObjectId templateSoccerPlayerId;

    @JsonView(JsonViews.NotForClient.class)
    public String optaPlayerId;

    @JsonView(JsonViews.Public.class)
    public String name;

    @JsonView(JsonViews.Public.class)
    public FieldPos fieldPos;

    @JsonView(JsonViews.Public.class)
    public int salary;

    @JsonView(JsonViews.Public.class)
    public int playedMatches;

    @JsonView(JsonViews.Public.class)
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
