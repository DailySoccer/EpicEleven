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

    // Constructor por defecto (necesario para Jongo: "unmarshall result to class")
    public SoccerTeam() {
    }

    public SoccerTeam(TemplateSoccerTeam template) {
        templateSoccerTeamId = template.templateSoccerTeamId;
        optaTeamId = template.optaTeamId;
        name = template.name;
        shortName = template.shortName;
    }

    /**
     * Setup Team (incrustando a los futbolistas en el equipo)
     */
    public static SoccerTeam create(MatchEvent matchEvent, TemplateSoccerTeam templateTeam) {
        SoccerTeam team = new SoccerTeam(templateTeam);

        Iterable<TemplateSoccerPlayer> playersTeamA = Model.templateSoccerPlayers().find("{ templateTeamId: # }", templateTeam.templateSoccerTeamId).as(TemplateSoccerPlayer.class);
        for(TemplateSoccerPlayer templateSoccer : playersTeamA) {
            team.soccerPlayers.add(new SoccerPlayer(templateSoccer));
        }
        return team;
    }
}
