package model.opta;

import model.GlobalDate;
import model.Model;
import org.bson.types.ObjectId;
import org.jongo.marshall.jackson.oid.Id;
import utils.ListUtils;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;

public class OptaCompetition {
    public boolean activated;
    public String competitionId;
    public String competitionCode;
    public String competitionName;
    public String seasonId;
    public Date createdAt;

    public OptaCompetition() {}
    public OptaCompetition(String competitionId, String competitionCode, String competitionName, String seasonId) {
        activated = false;
        this.competitionId = competitionId;
        this.competitionCode = competitionCode;
        this.competitionName = competitionName;
        this.seasonId = seasonId;
        this.createdAt = GlobalDate.getCurrentDate();
    }

    public static OptaCompetition findOne(String competitionId, String seasonId) {
        return Model.optaCompetitions().findOne("{competitionId: #, seasonId: #}", competitionId, seasonId).as(OptaCompetition.class);
    }

    public static boolean existsOneActivated(String competitionId) {
        return Model.optaCompetitions().findOne("{competitionId: #, activated: true}", competitionId).as(OptaCompetition.class) != null;
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
