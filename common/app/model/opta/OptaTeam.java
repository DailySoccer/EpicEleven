package model.opta;

import model.Model;

import java.util.ArrayList;
import java.util.Date;

public class OptaTeam {
    public String optaTeamId;
    public String name;
    public String shortName;
    public ArrayList<String> competitionIds;
    public Date updatedTime;
    public boolean dirty = true;

    public static OptaTeam findOne(String optaTeamId) {
        return Model.optaTeams().findOne("{optaTeamId: #}", optaTeamId).as(OptaTeam.class);
    }

}
