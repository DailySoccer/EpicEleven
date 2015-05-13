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
        if (this.typeId == 1) {
            if (outcome == 1) {
                this.typeId = OptaEventType.PASS_SUCCESSFUL.code;  //Pase exitoso-> 1001
            }
            else {
                this.typeId = OptaEventType.PASS_UNSUCCESSFUL.code;  //Pase fracasado -> 1002
            }
        }
        // Asistencia
        if (this.typeId == OptaEventType.PASS_SUCCESSFUL.code && qualifiers.contains(210)) {
            this.typeId = OptaEventType.ASSIST.code;  //Asistencia -> 1210
        }
        // Falta/Penalty infligido
        else if (this.typeId == OptaEventType.FOUL_RECEIVED.code && outcome == 0) {
            if (qualifiers.contains(9)) {
                this.typeId = OptaEventType.PENALTY_COMMITTED.code;  //Penalty infligido -> 1409
            } else {
                this.typeId = OptaEventType.FOUL_COMMITTED.code;  // Falta infligida -> 1004
            }
        }
        // Segunda tarjeta amarilla -> 1017
        else if (this.typeId == OptaEventType.YELLOW_CARD.code && qualifiers.contains(32)) {
            this.typeId = OptaEventType.SECOND_YELLOW_CARD.code;
        }
        // Tarjeta roja -> 1117
        else if (this.typeId == OptaEventType.YELLOW_CARD.code && qualifiers.contains(33)) {
            this.typeId = OptaEventType.RED_CARD.code;
        }
        // Penalty miss -> 1410
        else if ((this.typeId == OptaEventType.MISS.code || this.typeId == OptaEventType.POST.code ||
                this.typeId == OptaEventType.ATTEMPT_SAVED.code) &&
                outcome == 0 && qualifiers.contains(9)) {
            this.typeId = OptaEventType.PENALTY_FAILED.code;
        } else if (this.typeId == 16 && outcome == 1) {
            // Gol en contra -> 1699
            if (qualifiers.contains(28)) {
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
                } catch (NullPointerException e) {
                    Logger.info("Player not found: " + this.optaPlayerId);
                }
            }
        }
        // Penalty parado -> 1058
        else if (this.typeId == 58 && !qualifiers.contains(186)) {
            this.typeId = OptaEventType.GOALKEEPER_SAVES_PENALTY.code;
        }
        // Effective Tackle -> 1007
        else if (this.typeId == OptaEventType.TACKLE.code && outcome == 1) {
            this.typeId = OptaEventType.TACKLE_EFFECTIVE.code;
        }
        // Caught Offside -> 1072
        else if (this.typeId == 2 && qualifiers.contains(7)) {
            this.typeId = OptaEventType.CAUGHT_OFFSIDE.code;
            this.optaPlayerId = optaPlayerOffsideId;
        }
        // Player Saves -> 1010
        else if (this.typeId == OptaEventType.SAVE_GOALKEEPER.code && qualifiers.contains(94)) {
            this.typeId = OptaEventType.SAVE_PLAYER.code;
        }
        // Player Saves -> 1051
        else if (this.typeId == OptaEventType.ERROR.code) {
            if (qualifiers.contains(170)) {
                this.typeId = OptaEventType.DECISIVE_ERROR.code;
            }
            else if (!qualifiers.contains(169)) {
                this.typeId = OptaEventType._INVALID_.code;
            }
        }

        // Si no es un borrado, poner a INVALID si no está entre los que nos interesan
        if (this.typeId != 43) {
            this.typeId = OptaEventType.getEnum(this.typeId).code;
        }
    }

    public void insert() {
        Model.optaEvents().update("{eventId: #, teamId: #, gameId: #}", eventId, teamId, gameId).upsert().with(this);
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

    public static boolean isGameStarted(String gameId) {
        return (Model.optaEvents().findOne("{gameId: #, typeId: #, periodId: 1}", gameId, OptaEventType.PERIOD_BEGINS.code).as(OptaEvent.class) != null);
    }

    public static boolean isGameFinished(String gameId) {
        return (Model.optaEvents().findOne("{gameId: #, typeId: #}", gameId, OptaEventType.GAME_END.code).as(OptaEvent.class) != null);
    }
}
