package model;


import org.bson.types.ObjectId;
import org.jongo.marshall.jackson.oid.Id;
import java.util.Date;


public class LiveMatchEvent {
    @Id
    public ObjectId liveMatchEventId;

    public ObjectId templateMatchEventId;

    public SoccerTeam soccerTeamA;
    public SoccerTeam soccerTeamB;

    public Date startDate;

    public LiveMatchEvent() {}

    public LiveMatchEvent(TemplateMatchEvent templateMatchEvent) {
        templateMatchEventId = templateMatchEvent.templateMatchEventId;
        soccerTeamA = templateMatchEvent.soccerTeamA;
        soccerTeamB = templateMatchEvent.soccerTeamB;
        startDate = templateMatchEvent.startDate;
    }
}
