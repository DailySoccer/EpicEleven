package model;


import com.fasterxml.jackson.annotation.JsonView;
import com.mongodb.WriteConcern;
import model.opta.OptaPlayer;
import org.bson.types.ObjectId;
import org.joda.money.Money;
import org.jongo.marshall.jackson.oid.Id;
import play.Logger;
import play.Play;
import play.mvc.Result;
import utils.FileUtils;
import utils.ListUtils;
import utils.MoneyUtils;

import java.util.*;
import java.util.stream.Collectors;

public class TemplateSoccerPlayer implements JongoId {
    @Id
    public ObjectId templateSoccerPlayerId;

    @JsonView(JsonViews.NotForClient.class)
    public String optaPlayerId;

    public String name;
    public FieldPos fieldPos;
    public int salary;
    public int fantasyPoints;

    public ObjectId templateTeamId;

    @JsonView(JsonViews.NotForClient.class)
    public List<TemplateSoccerPlayerTag> tags = new ArrayList<>();

    @JsonView(JsonViews.NotForClient.class)
    public Date createdAt;

    @JsonView(JsonViews.Extended.class)
    public List<SoccerPlayerStats> stats = new ArrayList<>();

    @JsonView(JsonViews.Public.class)
    public int getPlayedMatches() {
        int numPlayed = 0;
        for (SoccerPlayerStats stat: stats) {
            if (stat.hasPlayed()) {
                numPlayed++;
            }
        }
        return numPlayed;
    }
    private void setPlayedMatches(int blah) { }    // Para poder deserializar lo que nos llega por la red sin usar FAIL_ON_UNKNOWN_PROPERTIES

    public TemplateSoccerPlayer() { }

    public TemplateSoccerPlayer(OptaPlayer optaPlayer, ObjectId aTemplateTeamId) {
        optaPlayerId = optaPlayer.optaPlayerId;
        name = optaPlayer.name;
        fieldPos = transformToFieldPosFromOptaPos(optaPlayer.position);
        templateTeamId = aTemplateTeamId;
        createdAt = GlobalDate.getCurrentDate();
        if (Play.application().configuration().getBoolean("activate_players_by_default")) {
            tags.add(TemplateSoccerPlayerTag.ACTIVE);
        }
    }

    public ObjectId getId() {
        return templateSoccerPlayerId;
    }

    static FieldPos transformToFieldPosFromOptaPos(String optaPosition) {
        FieldPos optaFieldPos = FieldPos.FORWARD;

        if      (optaPosition.startsWith("G"))  optaFieldPos = FieldPos.GOALKEEPER;
        else if (optaPosition.startsWith("D"))  optaFieldPos = FieldPos.DEFENSE;
        else if (optaPosition.startsWith("M"))  optaFieldPos = FieldPos.MIDDLE;
        else if (optaPosition.startsWith("F"))  optaFieldPos = FieldPos.FORWARD;
        else {
            Logger.error("Opta Position not registered yet: {}", optaPosition);
        }
        return optaFieldPos;
    }

    static public TemplateSoccerPlayer findOne(ObjectId templateSoccerPlayerId) {
        return Model.templateSoccerPlayers().findOne("{_id : #}", templateSoccerPlayerId).as(TemplateSoccerPlayer.class);
    }

    static public TemplateSoccerPlayer findOneFromOptaId(String optaPlayerId) {
        return Model.templateSoccerPlayers().findOne("{optaPlayerId: #}", optaPlayerId).as(TemplateSoccerPlayer.class);
    }

    static public boolean exists(String optaPlayerId) {
        return Model.templateSoccerPlayers().count("{optaPlayerId: #}", optaPlayerId) == 1;
    }

    public static List<TemplateSoccerPlayer> findAll() {
        return ListUtils.asList(Model.templateSoccerPlayers().find().as(TemplateSoccerPlayer.class));
    }

    public static List<TemplateSoccerPlayer> findAll(List<ObjectId> idList) {
        return ListUtils.asList(Model.findObjectIds(Model.templateSoccerPlayers(), "_id", idList).as(TemplateSoccerPlayer.class));
    }

    static public HashMap<String, TemplateSoccerPlayer> findAllAsMap(){
        HashMap<String, TemplateSoccerPlayer> map = new HashMap<>();
        for (TemplateSoccerPlayer optaPlayer: findAll()) {
            map.put(optaPlayer.optaPlayerId, optaPlayer);
        }
        return map;
    }

    static public List<TemplateSoccerPlayer> findAllFromTemplateTeam(ObjectId templateSoccerTeamId) {
        return ListUtils.asList(Model.templateSoccerPlayers().find("{ templateTeamId: # }", templateSoccerTeamId).as(TemplateSoccerPlayer.class));
    }

    static public List<TemplateSoccerPlayer> findAllActiveFromTemplateTeam(ObjectId templateSoccerTeamId) {
        return ListUtils.asList(Model.templateSoccerPlayers().find("{ templateTeamId: #, tags: {$elemMatch: {$eq: #}} }", templateSoccerTeamId, TemplateSoccerPlayerTag.ACTIVE).as(TemplateSoccerPlayer.class));
    }

    static public List<TemplateSoccerPlayer> findAllFromInstances(List<InstanceSoccerPlayer> instanceSoccerPlayers) {
        List<ObjectId> templateSoccerPlayerIds = new ArrayList<>();
        for (InstanceSoccerPlayer instanceSoccerPlayer: instanceSoccerPlayers) {
            templateSoccerPlayerIds.add(instanceSoccerPlayer.templateSoccerPlayerId);
        }
        return findAll(templateSoccerPlayerIds);
    }

    static public List<TemplateSoccerPlayer> findAllActiveFromTeams(List<TemplateSoccerTeam> templateSoccerTeams) {
        List<ObjectId> teamIds = new ArrayList<>();
        for (TemplateSoccerTeam team: templateSoccerTeams) {
            teamIds.add(team.templateSoccerTeamId);
        }
        return ListUtils.asList(Model.templateSoccerPlayers().find("{ templateTeamId: {$in: #}, tags: {$elemMatch: {$eq: #}} }", teamIds, TemplateSoccerPlayerTag.ACTIVE).as(TemplateSoccerPlayer.class));
    }

    public void updateStats(SoccerPlayerStats soccerPlayerStats) {
        boolean updateStats = true;

        // Buscar si ya tenemos estadísticas de ese mismo partido
        int index = searchIndexForMatchEvent(soccerPlayerStats.optaMatchEventId);
        // Son estadísticas nuevas?
        if (index == -1) {
            // Añadimos una nueva estadística
            stats.add(soccerPlayerStats);
        }
        else {
            // Actualizar las estadísticas
            stats.set(index, soccerPlayerStats);
        }

        fantasyPoints = calculateFantasyPointsFromStats();

        if (index == -1) {
            Model.templateSoccerPlayers()
                    .update("{optaPlayerId: #}", soccerPlayerStats.optaPlayerId)
                    .with("{$set: {fantasyPoints: #}, $push: {stats: #}}", fantasyPoints, soccerPlayerStats);
        }
        else {
            Model.templateSoccerPlayers()
                    .update("{optaPlayerId: #, \"stats.optaMatchEventId\":#}", soccerPlayerStats.optaPlayerId, soccerPlayerStats.optaMatchEventId)
                    .with("{$set: {fantasyPoints: #, \"stats.$\": #}}", fantasyPoints, soccerPlayerStats);
        }
    }

    public void updateEventStats() {
        stats.forEach( stat -> {
            if (stat.eventsCount == null) {
                stat.updateEventStats();
            }
        });
    }

    private int calculateFantasyPointsFromStats() {
        int numPlayedMatches = 0;
        int fantasyPointsMedia = 0;
        for (SoccerPlayerStats stat : stats) {
            if (stat.hasPlayed()) {
                fantasyPointsMedia += stat.fantasyPoints;
                numPlayedMatches++;
            }
        }
        if (numPlayedMatches > 0) {
            fantasyPointsMedia /= numPlayedMatches;
        }
        return fantasyPointsMedia;
    }

    private int searchIndexForMatchEvent(String optaMatchEventId) {
        int index = -1;
        for (int i=0; i<stats.size(); i++) {
            SoccerPlayerStats stat = stats.get(i);
            if (stat.optaMatchEventId.equals(optaMatchEventId)) {
                index = i;
                break;
            }
        }
        return index;
    }

    public boolean hasChanged(OptaPlayer optaPlayer) {
        return !optaPlayerId.equals(optaPlayer.optaPlayerId) ||
               !name.equals(optaPlayer.name) ||
               !fieldPos.equals(transformToFieldPosFromOptaPos(optaPlayer.position)) ||
               (TemplateSoccerTeam.findOne(templateTeamId, optaPlayer.teamId) == null);
    }

    public void changeDocument(OptaPlayer optaPlayer) {
        // optaPlayerId = optaPlayer.optaPlayerId;
        name = optaPlayer.name;
        fieldPos = transformToFieldPosFromOptaPos(optaPlayer.position);

        TemplateSoccerTeam templateSoccerTeam = TemplateSoccerTeam.findOneFromOptaId(optaPlayer.teamId);
        if (templateSoccerTeam != null) {
            templateTeamId = templateSoccerTeam.templateSoccerTeamId;
            updateDocument();
        }
        else {
            Logger.error("WTF 8791: TeamID({}) inválido", optaPlayer.teamId);
        }
    }

    public void updateDocument() {
        Model.templateSoccerPlayers().withWriteConcern(WriteConcern.SAFE).update("{optaPlayerId: #}", optaPlayerId).upsert().with(this);
    }

    public Money moneyToBuy(int managerLevel) {
        return TemplateSoccerPlayer.moneyToBuy(TemplateSoccerPlayer.levelFromSalary(salary), managerLevel);
    }

    public static Map<String, Long> frequencyFieldPos(List<TemplateSoccerPlayer> templateSoccerPlayers) {
        HashMap<String, Long> result = new HashMap<>();

        result.put(FieldPos.GOALKEEPER.name(), templateSoccerPlayers.stream().filter(templateSoccerPlayer -> templateSoccerPlayer.fieldPos.equals(FieldPos.GOALKEEPER)).count());
        result.put(FieldPos.DEFENSE.name(), templateSoccerPlayers.stream().filter(templateSoccerPlayer -> templateSoccerPlayer.fieldPos.equals(FieldPos.DEFENSE)).count());
        result.put(FieldPos.MIDDLE.name(), templateSoccerPlayers.stream().filter(templateSoccerPlayer -> templateSoccerPlayer.fieldPos.equals(FieldPos.MIDDLE)).count());
        result.put(FieldPos.FORWARD.name(), templateSoccerPlayers.stream().filter(templateSoccerPlayer -> templateSoccerPlayer.fieldPos.equals(FieldPos.FORWARD)).count());

        return result;
    }

    public static List<InstanceSoccerPlayer> instanceSoccerPlayersFromMatchEvents(List<TemplateMatchEvent> templateMatchEvents) {
        List<InstanceSoccerPlayer> result = new ArrayList<>();

        for (TemplateMatchEvent templateMatchEvent: templateMatchEvents) {
            List<TemplateSoccerPlayer> templateSoccerPlayers = templateMatchEvent.getTemplateSoccerPlayersActives();
            for (TemplateSoccerPlayer templateSoccerPlayer: templateSoccerPlayers) {
                result.add(new InstanceSoccerPlayer(templateSoccerPlayer));
            }
        }

        return result;
    }

    private static List<TemplateSoccerPlayer> filterSoccerPlayers(List<TemplateSoccerPlayer> templateSoccerPlayers, int managerLevel) {
        /*
        templateSoccerPlayers.stream().forEach( templateSoccerPlayer -> Logger.debug("{}: player: {} manager: {} money: {}",
                templateSoccerPlayer.name, TemplateSoccerPlayer.levelFromSalary(templateSoccerPlayer.salary), managerLevel, templateSoccerPlayer.moneyToBuy(managerLevel).toString() ));
        */
        return templateSoccerPlayers.stream().filter( templateSoccerPlayer -> templateSoccerPlayer.moneyToBuy(managerLevel).isZero()).collect(Collectors.toList() );
    }

    public static List<TemplateSoccerPlayer> soccerPlayersAvailables(TemplateSoccerTeam templateSoccerTeam, int managerLevel) {
        List<TemplateSoccerPlayer> players = templateSoccerTeam.getTemplateSoccerPlayers();
        return filterSoccerPlayers(players, managerLevel);
    }

    public static List<TemplateSoccerPlayer> soccerPlayersAvailables(TemplateMatchEvent templateMatchEvent, int managerLevel) {
        List<TemplateSoccerPlayer> availables = new ArrayList<>();

        availables.addAll(soccerPlayersAvailables(TemplateSoccerTeam.findOne(templateMatchEvent.templateSoccerTeamAId), managerLevel));
        availables.addAll(soccerPlayersAvailables(TemplateSoccerTeam.findOne(templateMatchEvent.templateSoccerTeamBId), managerLevel));

        //printFieldPos(String.format("%s => ", TemplateSoccerTeam.findOne(templateMatchEvent.templateSoccerTeamAId).name), availablesA);
        //printFieldPos(String.format("%s => ", TemplateSoccerTeam.findOne(templateMatchEvent.templateSoccerTeamBId).name), availablesB);

        return availables;
    }

    public static List<TemplateSoccerPlayer> soccerPlayersAvailables(List<ObjectId> templateMatchEventIds, int managerLevel) {
        List<TemplateSoccerPlayer> availables = new ArrayList<>();

        List<TemplateMatchEvent> templateMatchEvents = TemplateMatchEvent.findAll(templateMatchEventIds);

        Logger.debug("matches: {}", templateMatchEvents.size());
        for (TemplateMatchEvent templateMatchEvent : templateMatchEvents) {
            availables.addAll(soccerPlayersAvailables(templateMatchEvent, managerLevel));
        }

        return availables;
    }

    static public boolean isInvalidFromImport(OptaPlayer optaPlayer) {
        boolean invalid = (optaPlayer.teamId == null) || optaPlayer.teamId.isEmpty();

        if (!invalid) {
            TemplateSoccerTeam templateTeam = TemplateSoccerTeam.findOneFromOptaId(optaPlayer.teamId);
            invalid = (templateTeam == null);
        }

        return invalid;
    }

    static Integer[] LEVEL_SALARY = new Integer[]{
        6100, 6500, 6900, 7300, 8000, 100000, 100000, 100000, 100000, 100000, 100000
    };

    static public int levelFromSalary(int salary) {
        int level;
        for (level = 0; level<LEVEL_SALARY.length && salary>LEVEL_SALARY[level]; level++) {
        }
        return level;
    }

    static Integer[] LEVEL_PRICE = new Integer[]{
        0, 3, 7, 15, 30, 59, 63, 63, 63, 63, 63
    };

    static public Money moneyToBuy(int playerLevel, int managerLevel) {
        Money result = Money.zero(MoneyUtils.CURRENCY_GOLD);
        if (managerLevel < playerLevel) {
            result = result.plus( (playerLevel - managerLevel) * LEVEL_PRICE[playerLevel] );
        }
        return result;
    }
}
