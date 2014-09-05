package model.opta;

import java.util.ArrayList;
import java.util.Date;

public class OptaTeam {
    public String optaTeamId;
    public String name;
    public String shortName;
    public ArrayList<String> competitionIds;
    public Date updatedTime;
    public boolean dirty = true;
}
