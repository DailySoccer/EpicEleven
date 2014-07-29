package model;

import com.google.common.collect.ImmutableList;
import model.opta.*;
import play.Logger;

import java.util.*;

public class SoccerPlayerStats {

    public String optaPlayerId;
    public String optaMatchEventId;

    public int fantasyPoints;
    public int playedMinutes;
    public HashMap<Integer, Integer> events = new HashMap<>();

    // Constructor por defecto (necesario para Jongo: "unmarshall result to class")
    public SoccerPlayerStats() {
    }

    public SoccerPlayerStats(String optaPlayerId, String optaMatchEventId, int fantasyPoints) {
        this.optaMatchEventId = optaMatchEventId;
        this.optaPlayerId = optaPlayerId;
        this.fantasyPoints = fantasyPoints;
    }

    public void updateStats() {
        // Logger.debug("Stats: {} -----", optaPlayerId);

        OptaMatchEventStats matchEventStats = OptaMatchEventStats.findOne(optaMatchEventId);
        playedMinutes = (matchEventStats != null) ? matchEventStats.getPlayedMinutes(optaPlayerId) : 0;

        // Registrar los eventos "acumulados"
        for (StatType statType : StatType.values()) {
            int count = countStat(statType);
            if (count > 0) {
                events.put(statType.id, count);

                // Logger.debug("Stat: {} : {} count", statType, count);
            }
        }
    }

    public int countStat(StatType statType) {
        int count = 0;

        List<Integer> typeIds = statType.getEventTypes();
        if (typeIds != null) {
            count = (int) Model.optaEvents().count("{optaPlayerId: #, gameId: #, typeId:  {$in: #}}", optaPlayerId, optaMatchEventId, typeIds);
        }

        return count;
    }
}

/*
    Estadísticas:

    Goles: GOAL_SCORED_BY_GOALKEEPER + GOAL_SCORED_BY_DEFENDER + GOAL_SCORED_BY_MIDFIELDER + GOAL_SCORED_BY_FORWARD
    Tiros: ATTEMPT_SAVED + MISS + POST
    Pases:   PASS
    Asistencias: ASSIST
    Regates: TAKE_ON
    Recuperaciones: TACKLE + INTERCEPTION + TACKLE_EFFECTIVE
    Perdidas de balón: DISPOSSESSED + ERROR
    Faltas recibidas: FOUL_RECEIVED
    Faltas cometidas: FOUL_COMMITTED
    Tarjetas amarillas: YELLOW_CARD
    Tarjetas rojas: RED_CARD

    Goles encajados: GOAL_CONCEDED
    Paradas: SAVE + CLAIM
    Despejes: CLEARANCE + PUNCH
    Penaltis detenidos: GOALKEEPER_SAVES_PENALTY
*/
enum StatType {
    GOLES                   (0),    // GOAL_SCORED_BY_GOALKEEPER + GOAL_SCORED_BY_DEFENDER + GOAL_SCORED_BY_MIDFIELDER + GOAL_SCORED_BY_FORWARD
    TIROS                   (1),    // ATTEMPT_SAVED + MISS + POST
    PASES                   (2),    // PASS
    ASISTENCIAS             (3),    // ASSIST
    REGATES                 (4),    // TAKE_ON
    RECUPERACIONES          (5),    // TACKLE + INTERCEPTION + TACKLE_EFFECTIVE
    PERDIDAS_BALON          (6),    // DISPOSSESSED + ERROR
    FALTAS_RECIBIDAS        (7),    // FOUL_RECEIVED
    FALTAS_COMETIDAS        (8),    // FOUL_COMMITTED
    TARJETAS_AMARILLAS      (9),    // YELLOW_CARD
    TARJETAS_ROJAS          (10),   // RED_CARD
    GOLES_ENCAJADOS         (11),   // GOAL_CONCEDED
    PARADAS                 (12),   // SAVE + CLAIM
    DESPEJES                (13),   // CLEARANCE + PUNCH
    PENALTIS_DETENIDOS      (14);   // GOALKEEPER_SAVES_PENALTY

    public final int id;

    StatType(int id) {
        this.id = id;
    }

    List<Integer> getEventTypes() {
        switch(this) {
            case GOLES: return ImmutableList.of(
                    OptaEventType.GOAL_SCORED_BY_GOALKEEPER.getCode(),
                    OptaEventType.GOAL_SCORED_BY_DEFENDER.getCode(),
                    OptaEventType.GOAL_SCORED_BY_MIDFIELDER.getCode(),
                    OptaEventType.GOAL_SCORED_BY_FORWARD.getCode()
            );
            case TIROS: return ImmutableList.of(
                    OptaEventType.ATTEMPT_SAVED.getCode(),
                    OptaEventType.MISS.getCode(),
                    OptaEventType.POST.getCode()
            );
            case PASES: return ImmutableList.of(
                    OptaEventType.PASS.getCode()
            );
            case ASISTENCIAS: return ImmutableList.of(
                    OptaEventType.ASSIST.getCode()
            );
            case REGATES: return ImmutableList.of(
                    OptaEventType.TAKE_ON.getCode()
            );
            case RECUPERACIONES: return ImmutableList.of(
                    OptaEventType.TACKLE.getCode(),
                    OptaEventType.INTERCEPTION.getCode(),
                    OptaEventType.TACKLE_EFFECTIVE.getCode()
            );
            case PERDIDAS_BALON: return ImmutableList.of(
                    OptaEventType.DISPOSSESSED.getCode(),
                    OptaEventType.ERROR.getCode()
            );
            case FALTAS_RECIBIDAS: return ImmutableList.of(
                    OptaEventType.FOUL_RECEIVED.getCode()
            );
            case FALTAS_COMETIDAS: return ImmutableList.of(
                    OptaEventType.FOUL_COMMITTED.getCode()
            );
            case TARJETAS_AMARILLAS: return ImmutableList.of(
                    OptaEventType.YELLOW_CARD.getCode()
            );
            case TARJETAS_ROJAS: return ImmutableList.of(
                    OptaEventType.RED_CARD.getCode()
            );
            case GOLES_ENCAJADOS: return ImmutableList.of(
                    OptaEventType.GOAL_CONCEDED.getCode()
            );
            case PARADAS: return ImmutableList.of(
                    OptaEventType.SAVE.getCode(),
                    OptaEventType.CLAIM.getCode()
            );
            case DESPEJES: return ImmutableList.of(
                    OptaEventType.CLEARANCE.getCode(),
                    OptaEventType.PUNCH.getCode()
            );
            case PENALTIS_DETENIDOS: return ImmutableList.of(
                    OptaEventType.GOALKEEPER_SAVES_PENALTY.getCode()
            );
        }

        Logger.error("StatType: EventType {} unknown", this.id);
        return null;
    }
}
