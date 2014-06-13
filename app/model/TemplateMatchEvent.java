package model;


import org.bson.types.ObjectId;
import org.jongo.marshall.jackson.oid.Id;
import java.util.Date;


public class TemplateMatchEvent {
    @Id
    public ObjectId templateMatchEventId;

    public String optaMatchEventId;

    public SoccerTeam soccerTeamA;
    public SoccerTeam soccerTeamB;

    public Date startDate;

    public TemplateMatchEvent() {}
}
