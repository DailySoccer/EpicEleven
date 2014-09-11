package model.opta;

import model.Model;
import org.bson.types.ObjectId;
import utils.ListUtils;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;

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
}
