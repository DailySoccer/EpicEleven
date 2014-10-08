package model.opta;

import model.GlobalDate;
import model.Initializer;
import model.JongoId;
import model.Model;
import org.bson.types.ObjectId;
import utils.ListUtils;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;

public class OptaCompetition implements JongoId, Initializer {

    public ObjectId _id;
    public boolean activated;
    public String seasonCompetitionId;
    public String competitionId;
    public String competitionCode;
    public String competitionName;
    public String seasonId;
    public Date createdAt;

    public OptaCompetition() {}
    public OptaCompetition(String competitionId, String competitionCode, String competitionName, String seasonId) {
        activated = false;
        this.seasonCompetitionId = createId(seasonId, competitionId);
        this.competitionId = competitionId;
        this.competitionCode = competitionCode;
        this.competitionName = competitionName;
        this.seasonId = seasonId;
        this.createdAt = GlobalDate.getCurrentDate();
    }

    public ObjectId getId() {
        return _id;
    }

    public void Initialize() {}

    public static String createId(String seasonId, String competitionId) {
        return String.format("%s-%s", seasonId, competitionId);
    }

    public static OptaCompetition findOne(String seasonCompetitionId) {
        return Model.optaCompetitions().findOne("{seasonCompetitionId: #}", seasonCompetitionId).as(OptaCompetition.class);
    }

    public static OptaCompetition findOne(String competitionId, String seasonId) {
        return findOne(createId(seasonId, competitionId));
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
            competitionIds.add(optaCompetition.seasonCompetitionId);
        }
        return competitionIds;
    }

    static public HashMap<String, OptaCompetition> asMap(List<OptaCompetition> optaCompetitions){
        HashMap<String, OptaCompetition> map = new HashMap<>();
        for (OptaCompetition optaCompetition: optaCompetitions) {
            map.put(optaCompetition.seasonCompetitionId, optaCompetition);
        }
        return map;
    }

}
