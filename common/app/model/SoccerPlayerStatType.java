package model;


import com.google.common.collect.ImmutableList;
import model.opta.OptaEventType;
import play.Logger;

import java.util.List;

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
public enum SoccerPlayerStatType {
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

    SoccerPlayerStatType(int id) {
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
                    OptaEventType.PASS_SUCCESSFUL.getCode()
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
                    OptaEventType.PASS_UNSUCCESSFUL.getCode(),
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

