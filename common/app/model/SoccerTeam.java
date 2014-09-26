package model;

import com.fasterxml.jackson.annotation.JsonView;
import org.bson.types.ObjectId;
import java.util.ArrayList;
import java.util.List;

public class SoccerTeam {
    public ObjectId templateSoccerTeamId;

    @JsonView(JsonViews.NotForClient.class)
    public String optaTeamId;

    @JsonView(JsonViews.Public.class)
    public String name;

    @JsonView(JsonViews.Public.class)
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

    public SoccerPlayer findSoccerPlayer(ObjectId soccerPlayerId) {
        SoccerPlayer ret = null;
        for (SoccerPlayer soccerPlayer: soccerPlayers) {
            if (soccerPlayer.templateSoccerPlayerId.equals(soccerPlayerId)) {
                ret = soccerPlayer;
                break;
            }
        }
        return ret;
    }

    /**
     * Setup Team (incrustando a los futbolistas en el equipo)
     */
    public static SoccerTeam create(MatchEvent matchEvent, TemplateSoccerTeam templateTeam) {
        SoccerTeam team = new SoccerTeam(templateTeam);

        List<TemplateSoccerPlayer> playersTeam = TemplateSoccerPlayer.findAllActiveFromTemplateTeam(templateTeam.templateSoccerTeamId);
        for(TemplateSoccerPlayer templateSoccer : playersTeam) {
            team.soccerPlayers.add(new SoccerPlayer(templateSoccer));
        }
        return team;
    }
}
