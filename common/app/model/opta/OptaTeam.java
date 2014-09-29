package model.opta;

import model.GlobalDate;
import model.Model;
import org.jdom2.Element;
import utils.ListUtils;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;

public class OptaTeam {
    public static final String INVALID_TEAM = "-1";

    public String optaTeamId;
    public String name;
    public String shortName;
    public ArrayList<String> seasonCompetitionIds = new ArrayList<>();  // formato: <optaSeasonId>/<optaCompetitionId>
    public Date updatedTime;
    public boolean dirty = true;

    public OptaTeam() {}

    public OptaTeam(Element team) {

        optaTeamId = OptaProcessor.getStringId(team, "uID");

        name = team.getChild("Name").getContent().get(0).getValue();
        updatedTime = GlobalDate.getCurrentDate();

        if (null != team.getChild("SYMID") && team.getChild("SYMID").getContentSize() > 0) {
            shortName = team.getChild("SYMID").getContent().get(0).getValue();
        }
    }

    public static OptaTeam findOne(String optaTeamId) {
        return Model.optaTeams().findOne("{optaTeamId: #}", optaTeamId).as(OptaTeam.class);
    }

    static public List<OptaTeam> findAll() {
        return ListUtils.asList(Model.optaTeams().find().as(OptaTeam.class));
    }

    static public HashMap<String, OptaTeam> findAllAsMap(){
        HashMap<String, OptaTeam> map = new HashMap<>();
        for (OptaTeam optaTeam: findAll()) {
            map.put(optaTeam.optaTeamId, optaTeam);
        }
        return map;
    }

    static public void createInvalidTeam() {
        OptaTeam invalidTeam = new OptaTeam();
        invalidTeam.optaTeamId = INVALID_TEAM;
        invalidTeam.name = "-Unknown-";
        invalidTeam.shortName = "XXX";
        Model.optaTeams().insert(invalidTeam);
    }
}
