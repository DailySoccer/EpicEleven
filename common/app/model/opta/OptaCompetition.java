package model.opta;

import model.GlobalDate;
import model.JongoId;
import model.Model;
import org.bson.types.ObjectId;
import utils.ListUtils;

import java.util.*;

public class OptaCompetition implements JongoId {
    static public Date SEASON_DATE_START = new GregorianCalendar(2016, 8, 1).getTime();
    public static final String CURRENT_SEASON_ID = "2016";
    public static final String SPANISH_LA_LIGA = "23";
    public static final String PREMIER = "8";
    public static final String CHAMPIONS_LEAGUE = "5";

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


        Logger.error("OptaCompetition(1): seasonID "+CURRENT_SEASON_ID+" seasonId "+seasonId);
    }

    public ObjectId getId() {
        return _id;
    }

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

    static public List<OptaTeam> findTeamsByCompetitionId(String competitionId) {
        return OptaTeam.findAllByCompetition(createId(CURRENT_SEASON_ID, competitionId));
    }

    static public List<OptaTeam> findTeamsByCompetitionCode(String code) {
        List<OptaCompetition> competitions = ListUtils.asList(Model.optaCompetitions().find("{activated: true, competitionCode: #}", code).as(OptaCompetition.class));
        OptaCompetition competition = competitions.get(competitions.size()-1);
        return OptaTeam.findAllByCompetition(createId(CURRENT_SEASON_ID, competition.competitionId));
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
            if (!map.containsKey(optaCompetition.competitionId)) {
                map.put(optaCompetition.competitionId, optaCompetition);
            }
        }
        return map;
    }

    static public HashMap<String, OptaCompetition> asSeasonCompetitionMap(List<OptaCompetition> optaCompetitions){
        HashMap<String, OptaCompetition> map = new HashMap<>();
        for (OptaCompetition optaCompetition: optaCompetitions) {
            map.put(optaCompetition.seasonCompetitionId, optaCompetition);
        }
        return map;
    }

}
