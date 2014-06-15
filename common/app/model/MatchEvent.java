package model;

import org.bson.types.ObjectId;
import org.jongo.marshall.jackson.oid.Id;

import java.util.Date;

public class MatchEvent {

    @Id
    public ObjectId matchEventId;

    public SoccerTeam soccerTeamA;
    public SoccerTeam soccerTeamB;

    public Date startDate;      // Date stores milliseconds since the start of the Unix epoch in UTC

    public MatchEvent() {}
}
