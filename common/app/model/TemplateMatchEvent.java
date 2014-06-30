package model;


import org.bson.types.ObjectId;
import org.jongo.marshall.jackson.oid.Id;
import java.util.Date;
import model.opta.*;

public class TemplateMatchEvent {
    @Id
    public ObjectId templateMatchEventId;

    public String optaMatchEventId;

    public SoccerTeam soccerTeamA;
    public SoccerTeam soccerTeamB;

    public Date startDate;

    public TemplateMatchEvent() {}

    public boolean isEqual(OptaMatchEvent optaMatchEvent) {
        return optaMatchEventId.equals(optaMatchEvent.id) &&
               soccerTeamA.optaTeamId.equals(optaMatchEvent.homeTeamId) &&
               soccerTeamB.optaTeamId.equals(optaMatchEvent.awayTeamId) &&
               startDate.equals(optaMatchEvent.matchDate);
    }
}
