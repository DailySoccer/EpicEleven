package model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import model.opta.OptaEventType;
import org.bson.types.ObjectId;
import org.jongo.marshall.jackson.oid.Id;
import play.Logger;
import utils.ListUtils;

import java.util.*;
import java.util.stream.Collectors;

public class StatsSimulation {
    public enum TEAM {
        HOME,
        AWAY,
        MAX
    };
    @Id
    public ObjectId statsSimulationId;
    public String optaMatchEventId;

    public ObjectId templateMatchEventId;
    public String homeTeamId;
    public String awayTeamId;
    public String competitionId;
    public String seasonId;

    public Map<TEAM, List<ObjectId>> lineUp = new HashMap<>();

    public Map<TEAM, Map<ObjectId, Integer>> priorities = new HashMap<>();
    public Map<TEAM, Integer> prioritiesTotal = new HashMap<>();

    public Map<TEAM, Map<ObjectId, Integer>> foulPriorities = new HashMap<>();
    public Map<TEAM, Integer> foulPrioritiesTotal = new HashMap<>();

    public HashMap<ObjectId, HashMap<String, Integer>> statsCount = new HashMap<>();

    public StatsSimulation() {}

    public StatsSimulation(TemplateMatchEvent theTemplateMatchEvent) {
        templateMatchEvent = theTemplateMatchEvent;
        templateMatchEventId = theTemplateMatchEvent.templateMatchEventId;

        Logger.debug("MatchEventSimulation {}: init", templateMatchEventId);

        optaMatchEventId = templateMatchEvent.optaMatchEventId;
        homeTeamId = templateMatchEvent.optaTeamAId;
        awayTeamId = templateMatchEvent.optaTeamBId;
        competitionId = templateMatchEvent.optaCompetitionId;
        seasonId = templateMatchEvent.optaSeasonId;

        // Los futbolistas que componen los equipos
        List<TemplateSoccerPlayer> homePlayers = TemplateSoccerPlayer.findAllFromTemplateTeam(templateMatchEvent.templateSoccerTeamAId);
        registerEvents(homePlayers, homeTeamId);

        List<TemplateSoccerPlayer> awayPlayers = TemplateSoccerPlayer.findAllFromTemplateTeam(templateMatchEvent.templateSoccerTeamBId);
        registerEvents(awayPlayers, awayTeamId);

        // La alineación inicial de cada uno de los equipos
        List<TemplateSoccerPlayer> homeLineUp = selectLineUp(homePlayers);
        lineUp.put(TEAM.HOME, ListUtils.convertToIdList(homeLineUp));

        List<TemplateSoccerPlayer> awayLineUp = selectLineUp(awayPlayers);
        lineUp.put(TEAM.AWAY, ListUtils.convertToIdList(awayLineUp));

        // La prioridad de los futbolistas de la alineación de participar en el partido
        priorities.put(TEAM.HOME, calculatePriorities(homeLineUp, AttackEvents));
        prioritiesTotal.put(TEAM.HOME, 1);
        priorities.get(TEAM.HOME).forEach((key, priority) -> {
            prioritiesTotal.put(TEAM.HOME, prioritiesTotal.get(TEAM.HOME) + priority);
        });

        priorities.put(TEAM.AWAY, calculatePriorities(awayLineUp, AttackEvents));
        prioritiesTotal.put(TEAM.AWAY, 1);
        priorities.get(TEAM.AWAY).forEach((key, priority) -> {
            prioritiesTotal.put(TEAM.AWAY, prioritiesTotal.get(TEAM.AWAY) + priority);
        });

        // La prioridad de los futbolistas de la alineación de cometer faltas, obtener tarjetas amarillas/rojas
        foulPriorities.put(TEAM.HOME, calculatePriorities(homeLineUp, FoulEvents));
        foulPrioritiesTotal.put(TEAM.HOME, 1);
        foulPriorities.get(TEAM.HOME).forEach((key, priority) -> {
            foulPrioritiesTotal.put(TEAM.HOME, foulPrioritiesTotal.get(TEAM.HOME) + priority);
        });

        foulPriorities.put(TEAM.AWAY, calculatePriorities(awayLineUp, FoulEvents));
        foulPrioritiesTotal.put(TEAM.AWAY, 1);
        foulPriorities.get(TEAM.AWAY).forEach((key, priority) -> {
            foulPrioritiesTotal.put(TEAM.AWAY, foulPrioritiesTotal.get(TEAM.AWAY) + priority);
        });
    }

    public static StatsSimulation findOne(String optaMatchEventId) {
        return Model.statsSimulation().findOne("{optaMatchEventId: #}", optaMatchEventId).as(StatsSimulation.class);
    }

    public static StatsSimulation instance(TemplateMatchEvent templateMatchEvent) {
        StatsSimulation stats = findOne(templateMatchEvent.optaMatchEventId);
        if (stats == null) {
            stats = new StatsSimulation(templateMatchEvent);
            Model.statsSimulation().insert(stats);
        }
        return stats;
    }

    ObjectId getTarget(TEAM team) {
        ObjectId target = null;

        // Lanzamos un dado (cada jugador tiene como mínimo 1 número, aunque no tenga prioridades)
        int die = rnd.nextInt(prioritiesTotal.get(team) + priorities.get(team).size());
        for (Map.Entry<ObjectId, Integer> entry : priorities.get(team).entrySet()) {
            die -= entry.getValue() + 1;
            if (die <= 0) {
                target = entry.getKey();
                break;
            }
        }
        return target;
    }

    ObjectId getTargetThatFoulCommited(TEAM team) {
        ObjectId target = null;

        // Lanzamos un dado (cada jugador tiene como mínimo 1 número, aunque no tenga prioridades)
        int die = rnd.nextInt(foulPrioritiesTotal.get(team) + foulPriorities.get(team).size());
        for (Map.Entry<ObjectId, Integer> entry : foulPriorities.get(team).entrySet()) {
            die -= entry.getValue() + 1;
            if (die <= 0) {
                target = entry.getKey();
                break;
            }
        }

        return target;
    }

    // Seleccionar la acción del player (dada una lista de eventos posibles)
    OptaEventType selectAction(ObjectId templateSoccerPlayerId, OptaEventType[] events, OptaEventType defaultAction) {
        OptaEventType action = defaultAction;

        HashMap<String, Integer> statsPlayer = statsCount.get(templateSoccerPlayerId);
        int total = 0;
        for (OptaEventType optaEventType : events) {
            total += getStat(statsPlayer, optaEventType);
        }

        if (total > 0) {
            int die = rnd.nextInt(total);
            for (OptaEventType optaEventType : events) {
                die -= getStat(statsPlayer, optaEventType);
                if (die <= 0) {
                    action = optaEventType;
                    break;
                }
            }
        }
        else {
            // Logger.debug("Default Action: {}", defaultAction);
        }

        return action;
    }

    List<TemplateSoccerPlayer> filterByFieldPos(FieldPos fieldPos, List<TemplateSoccerPlayer> players) {
        return players.stream().filter(p -> p.fieldPos.equals(fieldPos)).collect(Collectors.toList());
    }

    List<TemplateSoccerPlayer> selectLineUp(List<TemplateSoccerPlayer> players) {
        List<TemplateSoccerPlayer> selected = new ArrayList<>();
        selected.addAll(selectPlayers(filterByFieldPos(FieldPos.GOALKEEPER, players), 1));
        selected.addAll(selectPlayers(filterByFieldPos(FieldPos.DEFENSE, players), 4));
        selected.addAll(selectPlayers(filterByFieldPos(FieldPos.MIDDLE, players), 4));
        selected.addAll(selectPlayers(filterByFieldPos(FieldPos.FORWARD, players), 2));
        return selected;
    }
    // Calcular las probabilidades de un player de realizar ciertas acciones
    Map<ObjectId, Integer> calculatePriorities(List<TemplateSoccerPlayer> players, OptaEventType[] events) {
        Map<ObjectId, Integer> priorities = new HashMap<>();
        int total = 0;
        for (TemplateSoccerPlayer player : players) {
            HashMap<String, Integer> stats = statsCount.get(player.templateSoccerPlayerId);
            int priority = 0;
            for (OptaEventType optaEventType : events) {
                priority += getStat(stats, optaEventType);
            }
            priorities.put(player.templateSoccerPlayerId, priority);
            total += priority;
        }
        /*
        for (TemplateSoccerPlayer player : players) {
            Logger.info("{}: {}%", player.name, priorities.get(player)*100/total);
        }
        */
        return priorities;
    }

    int getStat(HashMap<String, Integer> stats, OptaEventType optaEventType) {
        return stats.containsKey(optaEventType.toString()) ? stats.get(optaEventType.toString()) : 0;
    }

    // Obtener los eventos de un player durante los partidos (obtenido de los datos de Opta)
    void registerEvents(TemplateSoccerPlayer player, String optaTeamId) {
        if (!statsCount.containsKey(player.templateSoccerPlayerId)) {
            statsCount.put(player.templateSoccerPlayerId, new HashMap<>());
        }
        HashMap<String, Integer> stats = statsCount.get(player.templateSoccerPlayerId);

        stats.put(PLAYED_MATCHES, player.getPlayedMatches());

        int minutes = player.stats.stream().mapToInt(s -> s.playedMinutes).sum();
        stats.put(PLAYED_MINUTES, minutes);

        StringBuilder debugInfo = new StringBuilder();
        debugInfo.append(String.format("%s (%s) matches: %d \n",
                player.name, player.templateSoccerPlayerId.toString(), player.getPlayedMatches()));

        boolean inHome = optaTeamId.equals(homeTeamId);
        String eloString = ListUtils.asString(getTeamsELO(inHome ? homeTeamId : awayTeamId, inHome ? awayTeamId : homeTeamId));
        // Logger.debug("ELO: {}", eloString);

        for (OptaEventType optaEventType : OptaEventType.values()) {
            // Logger.debug("optaPlayerId: {}, competitionId: {}, typeId: {}", player.optaPlayerId, templateMatchEvent.optaCompetitionId, optaEventType.code);

            /*
            String query = String.format("{optaPlayerId: \"%s\", competitionId: \"%s\", typeId: %d, $or: [\n" +
                    "        {homeTeamId: {$in: [%s]}}, \n" +
                    "        {awayTeamId: {$in: [%s]}}\n" +
                    "    ]}", player.optaPlayerId, templateMatchEvent.optaCompetitionId, optaEventType.code, eloString, eloString);
            */
            // Queremos seleccionar partidos en los que el equipo esté en la misma situación que ahora (juegue en casa o fuera)
            /*
            String query = inHome
                    ? String.format("{optaPlayerId: \"%s\", competitionId: \"%s\", typeId: %d, awayTeamId: {$in: [%s]}}",
                    player.optaPlayerId, templateMatchEvent.optaCompetitionId, optaEventType.code, eloString)
                    : String.format("{optaPlayerId: \"%s\", competitionId: \"%s\", typeId: %d, homeTeamId: {$in: [%s]}}",
                    player.optaPlayerId, templateMatchEvent.optaCompetitionId, optaEventType.code, eloString);
            */
            String query = inHome
                    ? String.format("{optaPlayerId: \"%s\", competitionId: \"%s\", typeId: %d, homeTeamId: \"%s\"}",
                    player.optaPlayerId, templateMatchEvent.optaCompetitionId, optaEventType.code, homeTeamId)
                    : String.format("{optaPlayerId: \"%s\", competitionId: \"%s\", typeId: %d, awayTeamId: \"%s\"}",
                    player.optaPlayerId, templateMatchEvent.optaCompetitionId, optaEventType.code, awayTeamId);
            // Logger.debug("Query: {}", query);

            int count = (int) Model.optaEvents().count(query);
            // Logger.debug("{}: {}: {} count", player.name, optaTeamId, count);
            if (count > 0) {
                stats.put(optaEventType.toString(), count);
                debugInfo.append(optaEventType).append(" : ").append(count).append(" \n");
            }
        }

        // Logger.info(debugInfo.toString());
    }

    void registerEvents(List<TemplateSoccerPlayer> players, String optaTeamId) {
        players.forEach(player -> registerEvents(player, optaTeamId));
    }

    List<TemplateSoccerPlayer> selectPlayers(List<TemplateSoccerPlayer> players, int num) {
        List<TemplateSoccerPlayer> selected = new ArrayList<>();

        List<TemplateSoccerPlayer> sorted = players.stream().sorted((e1, e2) -> {
            HashMap<String, Integer> stats1 = statsCount.containsKey(e1.templateSoccerPlayerId)
                    ? statsCount.get(e1.templateSoccerPlayerId)
                    : null;
            HashMap<String, Integer> stats2 = statsCount.containsKey(e2.templateSoccerPlayerId)
                    ? statsCount.get(e2.templateSoccerPlayerId)
                    : null;

            if (stats1 == null) return -1;
            else if (stats2 == null) return 1;
            return Integer.compare(stats1.get(PLAYED_MINUTES), stats2.get(PLAYED_MINUTES));
        }).collect(Collectors.toList());

        Collections.reverse(sorted);
        selected.addAll(sorted.subList(0, num));

        return selected;
    }

    ArrayList<String> getTeamsELO(String ownerTeam, String opponentTeam) {
        ArrayList<String> result = new ArrayList<>();

        for(TeamELO team : ELO) {
            if (!team.optaId.equals(ownerTeam)) {
                result.add(team.optaId);
            }
        }

        return result;
    }

    class TeamELO {
        public String optaId;
        public Integer ELO;
        public TeamELO(String optaId, Integer elo) {
            this.optaId = optaId;
            this.ELO = elo;
        }
    }

    @JsonIgnore
    private TeamELO[] ELO = new TeamELO[] {
            new TeamELO("178", 2064), // Barcelona
            new TeamELO("186", 2055), // Real Madrid
            new TeamELO("175", 1948), // Atlético de Madrid
            new TeamELO("179", 1865), // Sevilla
            new TeamELO("191", 1863), // Valencia
            new TeamELO("176", 1809), // Celta de Vigo
            new TeamELO("449", 1804), // Villarreal
            new TeamELO("174", 1777), // Athletic Club
            new TeamELO("188", 1734), // Real Sociedad
            new TeamELO("177", 1730), // Espanyol
            new TeamELO("182", 1705), // Málaga
            new TeamELO("180", 1692), // Deportivo de la Coruña
            new TeamELO("184", 1690), // Rayo Vallecano
            new TeamELO("954", 1673), // Elche
            new TeamELO("855", 1658), // Levante
            new TeamELO("953", 1650), // Eibar
            new TeamELO("1450", 1648), // Getafe
            new TeamELO("5683", 1630), // Granada
            new TeamELO("1564", 1592), // Almería
            new TeamELO("952", 1548), // Córdoba
    };

    public static OptaEventType[] AttackEvents = {
            OptaEventType.PASS_SUCCESSFUL,
            OptaEventType.TAKE_ON,
            OptaEventType.ASSIST,
            OptaEventType.FOUL_RECEIVED,
            OptaEventType.PASS_UNSUCCESSFUL,
            OptaEventType.DISPOSSESSED,
            OptaEventType.ERROR,
            // OptaEventType.DECISIVE_ERROR,        // TODO: Quitado temporalmente por la gravedad del evento
            OptaEventType.ATTEMPT_SAVED,
            OptaEventType.POST,
            OptaEventType.MISS,
            OptaEventType.CAUGHT_OFFSIDE,
            // OptaEventType.OWN_GOAL,              // TODO: Quitado temporalmente por la gravedad del evento
            OptaEventType.GOAL_SCORED_BY_GOALKEEPER,
            OptaEventType.GOAL_SCORED_BY_DEFENDER,
            OptaEventType.GOAL_SCORED_BY_MIDFIELDER,
            OptaEventType.GOAL_SCORED_BY_FORWARD
    };

    OptaEventType[] getDefenseEventsFromAttackEvent(OptaEventType attackEvent) {
        OptaEventType[] result = new OptaEventType[]{};

        if (attackEvent.equals(OptaEventType.PASS_UNSUCCESSFUL)) {
            result = new OptaEventType[]{
                    OptaEventType.CLAIM,
                    OptaEventType.INTERCEPTION,
                    OptaEventType.CLEARANCE
            };
        }
        if (attackEvent.equals(OptaEventType.DISPOSSESSED)) {
            result = new OptaEventType[]{
                    OptaEventType.CLEARANCE,
                    OptaEventType.TACKLE_EFFECTIVE
            };
        }
        if (attackEvent.equals(OptaEventType.ATTEMPT_SAVED)) {
            result = new OptaEventType[]{
                    OptaEventType.CLEARANCE,
                    OptaEventType.SAVE_PLAYER,
                    OptaEventType.SAVE_GOALKEEPER
            };
        }

        return result;
    }

    public static OptaEventType[] FoulEvents = {
            OptaEventType.FOUL_COMMITTED,
            OptaEventType.YELLOW_CARD,
            OptaEventType.SECOND_YELLOW_CARD,
            OptaEventType.RED_CARD,
    };

    public static Set<OptaEventType> GoalEventsSet = new HashSet<OptaEventType>() {{
        add(OptaEventType.OWN_GOAL);
        add(OptaEventType.GOAL_SCORED_BY_GOALKEEPER);
        add(OptaEventType.GOAL_SCORED_BY_DEFENDER);
        add(OptaEventType.GOAL_SCORED_BY_MIDFIELDER);
        add(OptaEventType.GOAL_SCORED_BY_FORWARD);
    }};

    public static Set<OptaEventType> ChangeTeamEventsSet = new HashSet<OptaEventType>() {{
        add(OptaEventType.PASS_UNSUCCESSFUL);
        add(OptaEventType.DISPOSSESSED);
        add(OptaEventType.ERROR);
        add(OptaEventType.DECISIVE_ERROR);
        add(OptaEventType.ATTEMPT_SAVED);
        add(OptaEventType.POST);
        add(OptaEventType.MISS);
        add(OptaEventType.CAUGHT_OFFSIDE);
        add(OptaEventType.GOAL_SCORED_BY_GOALKEEPER);
        add(OptaEventType.GOAL_SCORED_BY_DEFENDER);
        add(OptaEventType.GOAL_SCORED_BY_MIDFIELDER);
        add(OptaEventType.GOAL_SCORED_BY_FORWARD);
    }};

    @JsonIgnore Random rnd = new Random();

    @JsonIgnore private TemplateMatchEvent templateMatchEvent;
    @JsonIgnore private final String PLAYED_MATCHES = "PLAYED_MATCHES";
    @JsonIgnore private final String PLAYED_MINUTES = "PLAYED_MINUTES";
}
