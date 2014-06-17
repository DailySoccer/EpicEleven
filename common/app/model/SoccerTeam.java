package model;

import org.bson.types.ObjectId;
import java.util.ArrayList;

public class SoccerTeam {
    public ObjectId templateSoccerTeamId;
    public String optaTeamId;
    public String name;
    public ArrayList<SoccerPlayer> soccerPlayers = new ArrayList<>();

    public int getFantasyPoints() {
        int totalFantasyPoints = 0;
        for (SoccerPlayer player: soccerPlayers) {
            totalFantasyPoints += player.fantasyPoints;
        }
        return totalFantasyPoints;
    }
}
