package model;


import org.bson.types.ObjectId;
import org.jongo.marshall.jackson.oid.Id;
import java.util.Date;


public class LiveMatchEvent {
    @Id
    public ObjectId liveMatchEventId;

    public ObjectId templateMatchEventId;
    public String optaMatchEventId;

    public SoccerTeam soccerTeamA;
    public SoccerTeam soccerTeamB;

    public Date startDate;

    public LiveMatchEvent() {}

    public LiveMatchEvent(TemplateMatchEvent templateMatchEvent) {
        templateMatchEventId = templateMatchEvent.templateMatchEventId;
        optaMatchEventId = templateMatchEvent.optaMatchEventId;
        soccerTeamA = templateMatchEvent.soccerTeamA;
        soccerTeamB = templateMatchEvent.soccerTeamB;
        startDate = templateMatchEvent.startDate;
    }

    public SoccerPlayer findTemplateSoccerPlayer(ObjectId templateSoccerPlayerId) {
        for(SoccerPlayer soccer : soccerTeamA.soccerPlayers) {
            if(soccer.templateSoccerPlayerId.compareTo(templateSoccerPlayerId) == 0)
                return soccer;
        }

        for(SoccerPlayer soccer : soccerTeamB.soccerPlayers) {
            if(soccer.templateSoccerPlayerId.compareTo(templateSoccerPlayerId) == 0)
                return soccer;
        }

        return null;
    }
}
