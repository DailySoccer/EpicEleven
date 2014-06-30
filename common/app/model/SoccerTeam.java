package model;

import org.bson.types.ObjectId;
import java.util.ArrayList;

public class SoccerTeam {
    public ObjectId templateSoccerTeamId;
    public String optaTeamId;
    public String name;
    public String shortName;
    public ArrayList<SoccerPlayer> soccerPlayers = new ArrayList<>();

    public int getFantasyPoints() {
        int totalFantasyPoints = 0;
        for (SoccerPlayer player: soccerPlayers) {
            totalFantasyPoints += player.fantasyPoints;
        }
        return totalFantasyPoints;
    }

    /**
     * Setup Team (incrustando a los futbolistas en el equipo)
     * @param templateTeam
     * @return
     */
    public static SoccerTeam create(TemplateSoccerTeam templateTeam) {
        SoccerTeam team = new SoccerTeam();
        team.templateSoccerTeamId = templateTeam.templateSoccerTeamId;
        team.optaTeamId = templateTeam.optaTeamId;
        team.name       = templateTeam.name;
        team.shortName  = templateTeam.shortName;

        Iterable<TemplateSoccerPlayer> playersTeamA = Model.templateSoccerPlayers().find("{ templateTeamId: # }", templateTeam.templateSoccerTeamId).as(TemplateSoccerPlayer.class);
        for(TemplateSoccerPlayer templateSoccer : playersTeamA) {
            team.soccerPlayers.add(new SoccerPlayer(templateSoccer));
        }
        return team;
    }
}
