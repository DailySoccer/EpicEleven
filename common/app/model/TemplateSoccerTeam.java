package model;


import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonView;
import com.mongodb.WriteConcern;
import model.opta.OptaCompetition;
import model.opta.OptaTeam;
import org.bson.types.ObjectId;
import org.jongo.marshall.jackson.oid.Id;
import play.Logger;
import utils.ListUtils;

import java.util.*;

public class TemplateSoccerTeam implements JongoId {
    @Id
    public ObjectId templateSoccerTeamId;

    @JsonView(JsonViews.NotForClient.class)
    public String optaTeamId;

    public String name;
    public String shortName;

    @JsonView(JsonViews.NotForClient.class)
    public Date createdAt;

    public TemplateSoccerTeam() { }

    public TemplateSoccerTeam(OptaTeam optaTeam) {
        optaTeamId = optaTeam.optaTeamId;
        name = optaTeam.name;
        shortName = optaTeam.shortName;
        createdAt = GlobalDate.getCurrentDate();
    }

    public ObjectId getId() {
        return templateSoccerTeamId;
    }

    public List<TemplateSoccerPlayer> getTemplateSoccerPlayersFilterBy(long fantasyPoints, long playedMatches, Date startDate) {
        return ListUtils.asList(Model.templateSoccerPlayers().find("{ templateTeamId: #, fantasyPoints: {$gte: #}, playedMatches: {$gte: #}, \"stats.startDate\": {$gte: #} }",
                templateSoccerTeamId, fantasyPoints, playedMatches, startDate).as(TemplateSoccerPlayer.class));
    }

    public List<TemplateSoccerPlayer> getTemplateSoccerPlayers() {
        return ListUtils.asList(Model.templateSoccerPlayers().find("{ templateTeamId: # }", templateSoccerTeamId).as(TemplateSoccerPlayer.class));
    }

    public List<TemplateSoccerPlayer> getTemplateSoccerPlayersWithSalary() {
        return ListUtils.asList(Model.templateSoccerPlayers().find("{ templateTeamId: #, salary: {$gt: 0} }", templateSoccerTeamId).as(TemplateSoccerPlayer.class));
    }

    static public TemplateSoccerTeam findOne(ObjectId templateSoccerTeamId) {
        return Model.templateSoccerTeams().findOne("{_id : #}", templateSoccerTeamId).as(TemplateSoccerTeam.class);
    }

    static public TemplateSoccerTeam findOneFromOptaId(String optaTeamId) {
        return Model.templateSoccerTeams().findOne("{optaTeamId: #}", optaTeamId).as(TemplateSoccerTeam.class);
    }

    static public TemplateSoccerTeam findOne(ObjectId templateSoccerTeamId, String optaTeamId) {
        return Model.templateSoccerTeams().findOne("{_id: #, optaTeamId: #}", templateSoccerTeamId, optaTeamId).as(TemplateSoccerTeam.class);
    }

    static public List<TemplateSoccerTeam> findAll() {
        return ListUtils.asList(Model.templateSoccerTeams().find().as(TemplateSoccerTeam.class));
    }

    static public List<TemplateSoccerTeam> findAll(List<ObjectId> templateSoccerTeamIds) {
        return ListUtils.asList(Model.findObjectIds(Model.templateSoccerTeams(), "_id", templateSoccerTeamIds).as(TemplateSoccerTeam.class));
    }

    static public List<TemplateSoccerTeam> findAllFromMatchEvents(List<TemplateMatchEvent> matchEvents) {
        List<ObjectId> teamIds = new ArrayList<>();
        for (TemplateMatchEvent matchEvent: matchEvents) {
            teamIds.add(matchEvent.templateSoccerTeamAId);
            teamIds.add(matchEvent.templateSoccerTeamBId);
        }
        return findAll(teamIds);
    }

    static public List<TemplateSoccerTeam> findAllByCompetition(String competitionId) {
        List<TemplateSoccerTeam> result = new ArrayList<>();

        List<OptaTeam> optaTeams = OptaCompetition.findTeamsByCompetitionId(competitionId);
        for (OptaTeam optaTeam : optaTeams) {
            result.add( findOneFromOptaId(optaTeam.optaTeamId) );
        }

        return result;
    }

    static public HashMap<ObjectId, TemplateSoccerTeam> findAllAsMap(){
        HashMap<ObjectId, TemplateSoccerTeam> map = new HashMap<>();
        for (TemplateSoccerTeam templateSoccerTeam: findAll()) {
            map.put(templateSoccerTeam.getId(), templateSoccerTeam);
        }
        return map;
    }

    static public HashMap<ObjectId, TemplateSoccerTeam> findAllAsMap(List<ObjectId> templateSoccerTeamIds){
        HashMap<ObjectId, TemplateSoccerTeam> map = new HashMap<>();
        for (TemplateSoccerTeam templateSoccerTeam: findAll(templateSoccerTeamIds)) {
            map.put(templateSoccerTeam.getId(), templateSoccerTeam);
        }
        return map;
    }

    public boolean hasChanged(OptaTeam optaTeam) {
        return !optaTeamId.equals(optaTeam.optaTeamId) ||
               !name.equals(optaTeam.name) ||
               !shortName.equals(optaTeam.shortName);
    }

    public void changeDocument(OptaTeam optaTeam) {
        // optaTeamId = optaTeam.optaTeamId;
        name = optaTeam.name;
        shortName = optaTeam.shortName;
        updateDocument();
    }

    public void updateDocument() {
        Model.templateSoccerTeams().withWriteConcern(WriteConcern.SAFE).update("{optaTeamId: #}", optaTeamId).upsert().with(this);
    }

    /**
     * Importar un optaTeam
     * @param optaTeam
     * @return
     */
    static public boolean importTeam(OptaTeam optaTeam) {
        TemplateSoccerTeam templateTeam = new TemplateSoccerTeam(optaTeam);
        templateTeam.updateDocument();
        return true;
    }

    static public boolean isInvalidFromImport(OptaTeam optaTeam) {
        return (optaTeam.name == null || optaTeam.name.isEmpty() || optaTeam.shortName == null || optaTeam.shortName.isEmpty());
    }

    static public void createInvalidTeam() {
        OptaTeam.createInvalidTeam();

        TemplateSoccerTeam invalidTeam = new TemplateSoccerTeam();
        invalidTeam.optaTeamId = OptaTeam.INVALID_TEAM;
        invalidTeam.name = "-Unknown-";
        invalidTeam.shortName = "XXX";
        invalidTeam.createdAt = GlobalDate.getCurrentDate();
        Model.templateSoccerTeams().insert(invalidTeam);
    }

    static public int getELO (String optaTeamId) {
        return ELO.containsKey(optaTeamId) ? ELO.get(optaTeamId) : ELO_DEFAULT;
    }

    static Map<ObjectId, Integer> getTemplateSoccerTeamsELO() {
        Map<ObjectId, Integer> result = new HashMap<>();

        List<TemplateSoccerTeam> templateSoccerTeamList = TemplateSoccerTeam.findAll();
        for (TemplateSoccerTeam templateSoccerTeam : templateSoccerTeamList) {
            result.put(templateSoccerTeam.templateSoccerTeamId, ELO.containsKey(templateSoccerTeam.optaTeamId) ? ELO.get(templateSoccerTeam.optaTeamId) : ELO_DEFAULT);
        }

        return result;
    }

    @JsonIgnore
    static final int ELO_DEFAULT = 1500;

    @JsonIgnore
    static private Map<String, Integer> ELO = new HashMap<String, Integer>() {{
            put("174", 1777); // Athletic Club
            put("175", 1948); // Atlético de Madrid
            put("176", 1809); // Celta de Vigo
            put("177", 1730); // Espanyol
            put("178", 2064); // Barcelona
            put("179", 1865); // Sevilla
            put("180", 1692); // Deportivo de la Coruña
            put("182", 1705); // Málaga
            put("184", 1690); // Rayo Vallecano
            put("186", 2055); // Real Madrid
            put("188", 1734); // Real Sociedad
            put("191", 1863); // Valencia
            put("449", 1804); // Villarreal
            put("855", 1658); // Levante
            put("952", 1548); // Córdoba
            put("953", 1650); // Eibar
            put("954", 1673); // Elche
            put("1450", 1648); // Getafe
            put("5683", 1630); // Granada
            put("1564", 1592); // Almería


            put("1", 1801); // Manchester United
            put("3", 1809); // Arsenal
            put("4", 1563); // Newcastle United
            put("6", 1728); // Tottenham Hotspur
            put("7", 1528); // Aston Villa
            put("8", 1806); // Chelsea
            put("11", 1698); // Everton
            put("13", 1636); // Leicester City
            put("14", 1708); // Liverpool
            put("20", 1682); // Southampton
            put("21", 1633); // West Ham United
            put("31", 1642); // Crystal Palace
            put("35", 1610); // West Bromwich Albion
            put("43", 1858); // Manchester City
            put("45", 1586); // Norwich City
            put("52", 1548); // Queens Park Rangers
            put("56", 1538); // Sunderland
            put("57", 1597); // Watford
            put("80", 1650); // Swansea City
            put("88", 1542); // Hull City
            put("90", 1543); // Burnley
            put("110", 1660); // Stoke City
            put("121", 1753); // Roma
            put("128", 1890); // Juventus
            put("143", 1693); // Lyon
            put("146", 1719); // Monaco
            put("149", 1889); // Paris Saint Germain
            put("156", 2007); // FC Bayern München
            put("157", 1813); // Borussia Dortmund
            put("164", 1798); // Bayer 04 Leverkusen
            put("167", 1728); // FC Schalke 04
            put("172", 1779); // VfL Wolfsburg
            put("194", ELO_DEFAULT); // Dynamo Kyiv
            put("201", 1823); // FC Porto
            put("202", 1254); // Olympiakos
            put("204", 1672); // PSV
            put("208", 1623); // Galatasaray
            put("215", 1652); // Ajax
            put("241", 1572); // RSC Anderlecht
            put("251", 1825); // Benfica
            put("255", 1689); // Sporting Lisbon
            put("394", 1567); // Dinamo Zagreb
            put("455", 1548); // Shakhtar Donetsk
            put("479", 1548); // APOEL Nicosia
            put("481", 1695); // FC Basel
            put("678", 1591); // KAA Gent
            put("683", ELO_DEFAULT); // Borussia Mönchengladbach
            put("993", 1537); // Malmö FF
            put("1007", ELO_DEFAULT); // Maccabi Tel Aviv
            put("1340", 1548); // CSKA Moscow
            put("1341", 1548); // Zenit St Petersburg
            put("2155", 1548); // BATE Borisov
            put("3033", 1441); // NK Maribor
            put("3751", ELO_DEFAULT); // FC Astana
            put("6128", 1548); // Ludogorets Razgrad
    }};
}