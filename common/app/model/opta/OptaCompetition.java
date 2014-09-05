package model.opta;

import model.Model;
import org.bson.types.ObjectId;
import org.jongo.marshall.jackson.oid.Id;
import utils.ListUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class OptaCompetition {
    public boolean activated;
    public String competitionId;
    public String competitionCode;
    public String competitionName;

    public OptaCompetition() {}
    public OptaCompetition(String competitionId, String competitionCode, String competitionName) {
        activated = false;
        this.competitionId = competitionId;
        this.competitionCode = competitionCode;
        this.competitionName = competitionName;
    }

    public static OptaCompetition findOne(String competitionId) {
        return Model.optaCompetitions().findOne("{competitionId: #}", competitionId).as(OptaCompetition.class);
    }

    static public List<OptaCompetition> findAll() {
        return ListUtils.asList(Model.optaCompetitions().find().as(OptaCompetition.class));
    }

    static public List<OptaCompetition> findAllActive() {
        return ListUtils.asList(Model.optaCompetitions().find("{activated: #}", true).as(OptaCompetition.class));
    }

    static public List<String> asIds(List<OptaCompetition> optaCompetitions) {
        List<String> competitionIds = new ArrayList<>();
        for (OptaCompetition optaCompetition : optaCompetitions) {
            competitionIds.add(optaCompetition.competitionId);
        }
        return competitionIds;
    }

    static public HashMap<String, OptaCompetition> asMap(List<OptaCompetition> optaCompetitions){
        HashMap<String, OptaCompetition> map = new HashMap<>();
        for (OptaCompetition optaCompetition: optaCompetitions) {
            map.put(optaCompetition.competitionId, optaCompetition);
        }
        return map;
    }
}
