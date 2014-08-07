package model.opta;

import model.Model;
import org.bson.types.ObjectId;
import org.jdom2.Element;
import org.jongo.marshall.jackson.oid.Id;
import play.Logger;
import utils.ListUtils;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;


/**
 * Created by gnufede on 28/05/14.
 */
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
        this.teamId = (int) Integer.parseInt(event.getAttributeValue("team_id"));
        this.periodId = (int) Integer.parseInt(event.getAttributeValue("period_id"));
        this.eventId = (int) Integer.parseInt(event.getAttributeValue("event_id"));
        this.typeId = (int) Integer.parseInt(event.getAttributeValue("type_id"));
        this.outcome = (int) Integer.parseInt(event.getAttributeValue("outcome"));
        this.timestamp = parseDate(event.getAttributeValue("timestamp"));
        this.lastModified = parseDate(event.getAttributeValue("last_modified"));
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
        //DERIVED EVENTS GO HERE
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
        // Tarjeta roja -> 1017
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

    /*
    public String getQualifier(int qualifierId) {
        //qualifiers.con
    }
    */

    public boolean hasChanged(OptaEvent other){
        if (other == this) {
            return false;
        }
        if (other == null) {
            return false;
        }
        return this.lastModified.before(other.lastModified);
    }

    static public List<OptaEvent> filter(String optaMatchId, String optaPlayerId) {
        Iterable<OptaEvent> optaEventResults = Model.optaEvents().find("{optaPlayerId: #, gameId: #}",
                optaPlayerId, optaMatchId).as(OptaEvent.class);
        return ListUtils.asList(optaEventResults);
    }

    public static Date parseDate(String timestamp, String timezone) {
        timezone = timezone!=null? timezone: "Europe/London";
        if (timezone.equals("BST")) {
            timezone = "GMT+01:00";
        }

        String dateConfig;
        SimpleDateFormat dateFormat;
        if (timestamp.indexOf('-') > 0) {
            dateConfig = timestamp.indexOf('T') > 0 ? "yyyy-MM-dd'T'hh:mm:ss.SSSz" : "yyyy-MM-dd hh:mm:ss.SSSz";
            dateFormat = new SimpleDateFormat(dateConfig.substring(0, timestamp.length()));
        }else{
            dateConfig = timestamp.indexOf('T') > 0 ? "yyyyMMdd'T'hhmmssZ" : "yyyyMMdd hhmmssZ";
            dateFormat = new SimpleDateFormat(dateConfig);
        }
        int plusPos = timestamp.indexOf('+');
        if (plusPos>=19) {
            if (timestamp.substring(plusPos, timestamp.length()).equals("+00:00")) {
                timestamp = timestamp.substring(0, plusPos);
                dateFormat = new SimpleDateFormat(dateConfig.substring(0, timestamp.length()));
            } else {
                Logger.info("Cant parse this date: " + timestamp);
            }
        }

        Date myDate = null;
        try {
            dateFormat.setTimeZone(TimeZone.getTimeZone(timezone));
            myDate = dateFormat.parse(timestamp);
        } catch (ParseException e) {
            Logger.error("WTF 7523862890", e);
        }
        return myDate;

    }

    public static Date parseDate(String timestamp) {
        return parseDate(timestamp, null);
    }

    public static boolean isGameStarted(String gameId) {
        return (Model.optaEvents().findOne("{gameId: #, typeId: 32, periodId: 1}", gameId).as(OptaEvent.class) != null);
    }

    public static boolean isGameFinished(String gameId) {
        return (Model.optaEvents().findOne("{gameId: #, typeId: 30, periodId: 14}", gameId).as(OptaEvent.class) != null);
    }
}
