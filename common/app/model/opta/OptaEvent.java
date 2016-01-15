package model.opta;

import model.GlobalDate;
import model.Model;
import org.bson.types.ObjectId;
import org.jdom2.Element;
import org.jongo.marshall.jackson.oid.Id;
import play.Logger;
import utils.ListUtils;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;


public class OptaEvent {
    @Id
    public ObjectId optaEventId;
    public String gameId;
    public String homeTeamId;
    public String awayTeamId;
    public String competitionId;
    public String seasonId;
    public int teamId;
    public int typeId;
    public int eventId;
    public String optaPlayerId;
    //ObjectId?
    public int periodId;
    public Date timestamp;
    public Date lastModified;
    public int min;
    public int sec;

    // Algunos eventos de los que generamos (p.ej. OptaEventType.GOAL_CONCEDED) pueden agrupar varios
    public Integer times = null;

    public int points;
    public ObjectId pointsTranslationId;

    public OptaEvent() {}

    public OptaEvent(int typeId, int eventId, String playerId, int teamId, String gameId, String competitionId,
                     String seasonId, Date timestamp, int points, ObjectId pointsTranslationId) {
        this.typeId = typeId;
        this.eventId = eventId;
        this.optaPlayerId = playerId;
        this.teamId = teamId;
        this.gameId = gameId;
        this.competitionId = competitionId;
        this.seasonId = seasonId;

        this.timestamp = timestamp;

        this.points = points;
        this.pointsTranslationId = pointsTranslationId;
    }


    public OptaEvent(Element event, Element game) {
        this.gameId = game.getAttributeValue("id");
        this.homeTeamId = game.getAttributeValue("home_team_id");
        this.awayTeamId = game.getAttributeValue("away_team_id");
        this.competitionId = game.getAttributeValue("competition_id");
        this.seasonId = game.getAttributeValue("season_id");
        this.teamId = Integer.parseInt(event.getAttributeValue("team_id"));
        this.periodId = Integer.parseInt(event.getAttributeValue("period_id"));
        this.eventId = Integer.parseInt(event.getAttributeValue("event_id"));
        this.typeId = Integer.parseInt(event.getAttributeValue("type_id"));
        int outcome = Integer.parseInt(event.getAttributeValue("outcome"));
        this.timestamp = GlobalDate.parseDate(event.getAttributeValue("timestamp"), null);
        this.lastModified = GlobalDate.parseDate(event.getAttributeValue("last_modified"), null);
        this.min = Integer.parseInt(event.getAttributeValue("min"));
        this.sec = Integer.parseInt(event.getAttributeValue("sec"));

        ArrayList<Integer> qualifiers = new ArrayList<>();

        final int TYPEID_PASS = 1;
        final int TYPEID_GOAL = 16;
        final int TYPEID_PENALTY_FACED = 58;
        final int TYPEID_OFFSIDE_PASS = 2;
        final int TYPEID_DELETED_EVENT = 43;
        final int QUALIFIER_HEAD = 15;
        final int QUALIFIER_ASSISTED_ATTEMPT = 29;
        final int QUALIFIER_ASSIST = 210;
        final int QUALIFIER_SECOND_YELLOW_CARD = 32;
        final int QUALIFIER_RED_CARD = 33;
        final int QUALIFIER_PENALTY = 9;
        final int QUALIFIER_OWN_GOAL = 28;
        final int QUALIFIER_SCORED = 186;
        final int QUALIFIER_PLAYERS_CAUGHT_OFFSIDE = 7;
        final int QUALIFIER_DEF_BLOCK = 94;
        final int QUALIFIER_LEADING_TO_GOAL = 170;
        final int QUALIFIER_LEADING_TO_ATTEMPT = 169;

        if (event.getAttribute("player_id") != null) {
            this.optaPlayerId = event.getAttributeValue("player_id");
        }

        String optaPlayerOffsideId = "<player_offside>";
        if (event.getChildren("Q") != null) {
            List<Element> qualifierList = event.getChildren("Q");
            qualifiers = new ArrayList<>((qualifierList).size());
            for (Element qualifier : qualifierList) {
                Integer tempQualifier = Integer.parseInt(qualifier.getAttributeValue("qualifier_id"));
                qualifiers.add(tempQualifier);

                // Se ha dejado a un futbolista en fuera de juego?
                if (tempQualifier == 7) {
                    optaPlayerOffsideId = qualifier.getAttributeValue("value");
                    // Logger.info("optaOtherPlayerId: {}", optaPlayerOffsideId);
                }
            }
        }
        // DERIVED EVENTS GO HERE
        // Pase exitoso o fracasado
        if (this.typeId == TYPEID_PASS) {
            if (outcome == 1) {
                if (qualifiers.contains(QUALIFIER_ASSIST))
                    this.typeId = OptaEventType.ASSIST.code;  //Asistencia -> 1210
                else
                    this.typeId = OptaEventType.PASS_SUCCESSFUL.code;  //Pase exitoso-> 1001
            }
            else {
                this.typeId = OptaEventType.PASS_UNSUCCESSFUL.code;  //Pase fracasado -> 1002
            }
        }
        // Falta/Penalty infligido
        else if (this.typeId == OptaEventType.FOUL_RECEIVED.code && outcome == 0) {
            if (qualifiers.contains(QUALIFIER_PENALTY)) {
                this.typeId = OptaEventType.PENALTY_COMMITTED.code;  //Penalty infligido -> 1409
            } else {
                this.typeId = OptaEventType.FOUL_COMMITTED.code;  // Falta infligida -> 1004
            }
        }
        // Tarjeta amarilla
        else if (this.typeId == OptaEventType.YELLOW_CARD.code) {
            // Segunda tarjeta amarilla -> 1017
            if (qualifiers.contains(QUALIFIER_SECOND_YELLOW_CARD)) {
                this.typeId = OptaEventType.SECOND_YELLOW_CARD.code;
            }
            // Tarjeta roja -> 1117
            else if (qualifiers.contains(QUALIFIER_RED_CARD)) {
                this.typeId = OptaEventType.RED_CARD.code;
            }
        }
        else if (this.typeId == OptaEventType.MISS.code || this.typeId == OptaEventType.POST.code || this.typeId == OptaEventType.ATTEMPT_SAVED.code) {
            // Penalty miss -> 1410
            if (outcome == 0 && qualifiers.contains(QUALIFIER_PENALTY)) {
                this.typeId = OptaEventType.PENALTY_FAILED.code;
            }
            else {
                // Tiro utilizando la cabeza?
                boolean head = qualifiers.contains(QUALIFIER_HEAD);
                if (head) {
                    // Logger.info("-----> Tiro: HEAD <----------------------------------------------------------");
                }
                boolean assisted = qualifiers.contains(QUALIFIER_ASSISTED_ATTEMPT);
                if (assisted) {
                    // Logger.info("-----> Tiro: ASSISTED <----------------------------------------------------------");
                }
            }
        } else if (this.typeId == TYPEID_GOAL && outcome == 1) {
            // Gol en contra -> 1699
            if (qualifiers.contains(QUALIFIER_OWN_GOAL)) {
                this.typeId = OptaEventType.OWN_GOAL.code;
            } else {
                // Diferencias en goles:
                try {
                    OptaPlayer scorer = Model.optaPlayers().findOne("{optaPlayerId: #}", this.optaPlayerId).as(OptaPlayer.class);
                    if (scorer.position.equals("Goalkeeper")) {
                        // Gol del portero
                        this.typeId = OptaEventType.GOAL_SCORED_BY_GOALKEEPER.code;
                    } else if (scorer.position.equals("Defender")) {
                        // Gol del defensa
                        this.typeId = OptaEventType.GOAL_SCORED_BY_DEFENDER.code;
                    } else if (scorer.position.equals("Midfielder")) {
                        // Gol del medio
                        this.typeId = OptaEventType.GOAL_SCORED_BY_MIDFIELDER.code;
                    } else if (scorer.position.equals("Forward")) {
                        // Gol del delantero
                        this.typeId = OptaEventType.GOAL_SCORED_BY_FORWARD.code;
                    }
                    boolean head = qualifiers.contains(QUALIFIER_HEAD);
                    if (head) {
                        // Logger.info("-----> Gol: HEAD <----------------------------------------------------------");
                    }
                    boolean assisted = qualifiers.contains(QUALIFIER_ASSISTED_ATTEMPT);
                    if (assisted) {
                        // Logger.info("-----> Gol: ASSISTED <----------------------------------------------------------");
                    }
                    boolean penalty = qualifiers.contains(QUALIFIER_PENALTY);
                    if (penalty) {
                        // Logger.info("-----> Gol: PENALTY <----------------------------------------------------------");
                    }
                } catch (NullPointerException e) {
                    Logger.info("Player not found: " + this.optaPlayerId);
                }
            }
        }
        // Penalty parado -> 1058
        else if (this.typeId == TYPEID_PENALTY_FACED && !qualifiers.contains(QUALIFIER_SCORED)) {
            this.typeId = OptaEventType.GOALKEEPER_SAVES_PENALTY.code;
        }
        // Effective Tackle -> 1007
        else if (this.typeId == OptaEventType.TACKLE.code && outcome == 1) {
            this.typeId = OptaEventType.TACKLE_EFFECTIVE.code;
        }
        // Caught Offside -> 1072
        else if (this.typeId == TYPEID_OFFSIDE_PASS && qualifiers.contains(QUALIFIER_PLAYERS_CAUGHT_OFFSIDE)) {
            this.typeId = OptaEventType.CAUGHT_OFFSIDE.code;
            this.optaPlayerId = optaPlayerOffsideId;
        }
        // Player Saves -> 1010
        else if (this.typeId == OptaEventType.SAVE_GOALKEEPER.code && qualifiers.contains(QUALIFIER_DEF_BLOCK)) {
            this.typeId = OptaEventType.SAVE_PLAYER.code;
        }
        // Player Saves -> 1051
        else if (this.typeId == OptaEventType.ERROR.code) {
            if (qualifiers.contains(QUALIFIER_LEADING_TO_GOAL)) {
                this.typeId = OptaEventType.DECISIVE_ERROR.code;
            }
            else if (!qualifiers.contains(QUALIFIER_LEADING_TO_ATTEMPT)) {
                this.typeId = OptaEventType._INVALID_.code;
            }
        }

        // Si no es un borrado, poner a INVALID si no está entre los que nos interesan
        if (this.typeId != TYPEID_DELETED_EVENT) {
            this.typeId = OptaEventType.getEnum(this.typeId).code;
        }
    }

    public OptaEvent insert() {
        Model.optaEvents().update("{eventId: #, teamId: #, gameId: #}", eventId, teamId, gameId).upsert().with(this);
        return this;
    }

    static public OptaEvent findLast(String optaMatchEventId) {
        OptaEvent lastEvent = null;

        Iterable<OptaEvent> optaEvents = Model.optaEvents().find("{gameId: #}", optaMatchEventId).sort("{timestamp: -1}").limit(1).as(OptaEvent.class);
        if (optaEvents != null && optaEvents.iterator().hasNext()) {
            lastEvent = optaEvents.iterator().next();
        }

        return lastEvent;
    }

    static public List<OptaEvent> filter(String optaMatchId, String optaPlayerId) {
        return ListUtils.asList(Model.optaEvents().find("{optaPlayerId: #, gameId: #}", optaPlayerId, optaMatchId).as(OptaEvent.class));
    }

    static public List<OptaEvent> filterByOptaPlayer(String optaPlayerId) {
        return ListUtils.asList(Model.optaEvents().find("{optaPlayerId: #}", optaPlayerId).as(OptaEvent.class));
    }

    static public List<OptaEvent> filterByOptaTeam(String optaTeamId) {
        return ListUtils.asList(Model.optaEvents().find("{teamId: #, $or: [{homeTeamId: #}, {awayTeamId: #}], optaPlayerId: {$exists: true}}", Integer.valueOf(optaTeamId), optaTeamId, optaTeamId).as(OptaEvent.class));
    }

    public static boolean isGameStarted(String gameId) {
        return (Model.optaEvents().findOne("{gameId: #, typeId: #, periodId: 1}", gameId, OptaEventType.PERIOD_BEGINS.code).as(OptaEvent.class) != null);
    }

    public static boolean isGameFinished(String gameId) {
        return (Model.optaEvents().findOne("{gameId: #, typeId: #}", gameId, OptaEventType.GAME_END.code).as(OptaEvent.class) != null);
    }
}
