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
    public int parentId;
    public int periodId;
    // <DEBUG>
    public int outcome;
    public ArrayList<Integer> qualifiers = new ArrayList<>();
    // </DEBUG>
    public Date timestamp;
    public Date lastModified;
    public int min;
    public int sec;

    public int points;
    public ObjectId pointsTranslationId;

    public OptaEvent(){}
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
        this.outcome = Integer.parseInt(event.getAttributeValue("outcome"));
        this.timestamp = GlobalDate.parseDate(event.getAttributeValue("timestamp"), null);
        this.lastModified = GlobalDate.parseDate(event.getAttributeValue("last_modified"), null);
        this.min = Integer.parseInt(event.getAttributeValue("min"));
        this.sec = Integer.parseInt(event.getAttributeValue("sec"));

        if (event.getAttribute("player_id") != null) {
            this.optaPlayerId = event.getAttributeValue("player_id");
        }

        String optaPlayerOffsideId = "<player_offside>";
        if (event.getChildren("Q") != null) {
            List<Element> qualifierList = event.getChildren("Q");
            this.qualifiers = new ArrayList<>((qualifierList).size());
            for (Element qualifier : qualifierList) {
                Integer tempQualifier = Integer.parseInt(qualifier.getAttributeValue("qualifier_id"));
                this.qualifiers.add(tempQualifier);

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
            if (this.outcome == 1) {
                this.typeId = OptaEventType.PASS_SUCCESSFUL._code;  //Pase exitoso-> 1001
            }
            else {
                this.typeId = OptaEventType.PASS_UNSUCCESSFUL._code;  //Pase fracasado -> 1002
            }
        }
        // Asistencia
        if (this.typeId == OptaEventType.PASS_SUCCESSFUL._code && this.qualifiers.contains(210)) {
            this.typeId = OptaEventType.ASSIST._code;  //Asistencia -> 1210
        }
        // Falta/Penalty infligido
        else if (this.typeId == OptaEventType.FOUL_RECEIVED._code && this.outcome == 0) {
            if (this.qualifiers.contains(9)) {
                this.typeId = OptaEventType.PENALTY_COMMITTED._code;  //Penalty infligido -> 1409
            } else {
                this.typeId = OptaEventType.FOUL_COMMITTED._code;  // Falta infligida -> 1004
            }
        }
        // Segunda tarjeta amarilla -> 1017
        else if (this.typeId == OptaEventType.YELLOW_CARD._code && this.qualifiers.contains(32)) {
            this.typeId = OptaEventType.SECOND_YELLOW_CARD._code;
        }
        // Tarjeta roja -> 1117
        else if (this.typeId == OptaEventType.YELLOW_CARD._code && this.qualifiers.contains(33)) {
            this.typeId = OptaEventType.RED_CARD._code;
        }
        // Penalty miss -> 1410
        else if ((this.typeId == OptaEventType.MISS._code || this.typeId == OptaEventType.POST._code ||
                this.typeId == OptaEventType.ATTEMPT_SAVED._code) &&
                this.outcome == 0 && this.qualifiers.contains(9)) {
            this.typeId = OptaEventType.PENALTY_FAILED._code;
        } else if (this.typeId == 16 && this.outcome == 1) {
            // Gol en contra -> 1699
            if (this.qualifiers.contains(28)) {
                this.typeId = OptaEventType.OWN_GOAL._code;
            } else {
                // Diferencias en goles:
                try {
                    OptaPlayer scorer = Model.optaPlayers().findOne("{optaPlayerId: #}", this.optaPlayerId).as(OptaPlayer.class);
                    if (scorer.position.equals("Goalkeeper")) {
                        // Gol del portero
                        this.typeId = OptaEventType.GOAL_SCORED_BY_GOALKEEPER._code;
                    } else if (scorer.position.equals("Defender")) {
                        // Gol del defensa
                        this.typeId = OptaEventType.GOAL_SCORED_BY_DEFENDER._code;
                    } else if (scorer.position.equals("Midfielder")) {
                        // Gol del medio
                        this.typeId = OptaEventType.GOAL_SCORED_BY_MIDFIELDER._code;
                    } else if (scorer.position.equals("Forward")) {
                        // Gol del delantero
                        this.typeId = OptaEventType.GOAL_SCORED_BY_FORWARD._code;
                    }
                } catch (NullPointerException e) {
                    Logger.info("Player not found: " + this.optaPlayerId);
                }
            }
        }
        // Penalty parado -> 1058
        else if (this.typeId == 58 && !this.qualifiers.contains(186)) {
            this.typeId = OptaEventType.GOALKEEPER_SAVES_PENALTY._code;
        }
        // Effective Tackle -> 1007
        else if (this.typeId == OptaEventType.TACKLE._code && this.outcome == 1) {
            this.typeId = OptaEventType.TACKLE_EFFECTIVE._code;
        }
        // Caught Offside -> 1072
        else if (this.typeId == 2 && this.qualifiers.contains(7)) {
            this.typeId = OptaEventType.CAUGHT_OFFSIDE._code;
            this.optaPlayerId = optaPlayerOffsideId;
        }
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
        return (Model.optaEvents().findOne("{gameId: #, typeId: 32, periodId: 1}", gameId).as(OptaEvent.class) != null);
    }

    public static boolean isGameFinished(String gameId) {
        return (Model.optaEvents().findOne("{gameId: #, typeId: 30, periodId: 14}", gameId).as(OptaEvent.class) != null);
    }
}
