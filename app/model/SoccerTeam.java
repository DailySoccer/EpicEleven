package model;

import org.bson.types.ObjectId;
import java.util.ArrayList;

public class SoccerTeam {
    public ObjectId templateSoccerTeamId;
    public String name;
    public ArrayList<SoccerPlayer> soccerPlayers = new ArrayList<>();
}
