package model;


import com.google.common.collect.ImmutableList;
import model.opta.OptaEventType;

import java.util.List;

/*
    Estadísticas:

    Goles: GOAL_SCORED_BY_GOALKEEPER + GOAL_SCORED_BY_DEFENDER + GOAL_SCORED_BY_MIDFIELDER + GOAL_SCORED_BY_FORWARD
    Tiros: ATTEMPT_SAVED + MISS + POST
    Pases:   PASS
    Asistencias: ASSIST
    Regates: TAKE_ON
    Recuperaciones: SAVE_PLAYER + TACKLE + INTERCEPTION + TACKLE_EFFECTIVE
    Perdidas de balón: DISPOSSESSED + ERROR
    Faltas recibidas: FOUL_RECEIVED
    Faltas cometidas: FOUL_COMMITTED
    Tarjetas amarillas: YELLOW_CARD
    Tarjetas rojas: RED_CARD

    Goles encajados: GOAL_CONCEDED
    Paradas: SAVE_GOALKEEPER + CLAIM
    Despejes: CLEARANCE + PUNCH
    Penaltis detenidos: GOALKEEPER_SAVES_PENALTY
*/
public enum SoccerPlayerStatType {
    GOLES                   (0),    // GOAL_SCORED_BY_GOALKEEPER + GOAL_SCORED_BY_DEFENDER + GOAL_SCORED_BY_MIDFIELDER + GOAL_SCORED_BY_FORWARD
    TIROS                   (1),    // ATTEMPT_SAVED + MISS + POST
    PASES                   (2),    // PASS
    ASISTENCIAS             (3),    // ASSIST
    REGATES                 (4),    // TAKE_ON
    RECUPERACIONES          (5),    // SAVE_PLAYER + TACKLE + INTERCEPTION + TACKLE_EFFECTIVE
    PERDIDAS_BALON          (6),    // DISPOSSESSED + ERROR
    FALTAS_RECIBIDAS        (7),    // FOUL_RECEIVED
    FALTAS_COMETIDAS        (8),    // FOUL_COMMITTED
    TARJETAS_AMARILLAS      (9),    // YELLOW_CARD
    TARJETAS_ROJAS          (10),   // RED_CARD
    GOLES_ENCAJADOS         (11),   // GOAL_CONCEDED
    PARADAS                 (12),   // SAVE_GOALKEEPER + CLAIM
    DESPEJES                (13),   // CLEARANCE + PUNCH
    PENALTIS_DETENIDOS      (14);   // GOALKEEPER_SAVES_PENALTY

    public final int id;

    SoccerPlayerStatType(int id) {
        this.id = id;
    }

    List<Integer> getEventTypes() {
        switch(this) {
            case GOLES: return ImmutableList.of(
                    OptaEventType.GOAL_SCORED_BY_GOALKEEPER.code,
                    OptaEventType.GOAL_SCORED_BY_DEFENDER.code,
                    OptaEventType.GOAL_SCORED_BY_MIDFIELDER.code,
                    OptaEventType.GOAL_SCORED_BY_FORWARD.code
            );
            case TIROS: return ImmutableList.of(
                    OptaEventType.ATTEMPT_SAVED.code,
                    OptaEventType.MISS.code,
                    OptaEventType.POST.code
            );
            case PASES: return ImmutableList.of(
                    OptaEventType.PASS_SUCCESSFUL.code
            );
            case ASISTENCIAS: return ImmutableList.of(
                    OptaEventType.ASSIST.code
            );
            case REGATES: return ImmutableList.of(
                    OptaEventType.TAKE_ON.code
            );
            case RECUPERACIONES: return ImmutableList.of(
                    OptaEventType.SAVE_PLAYER.code,
                    OptaEventType.TACKLE.code,
                    OptaEventType.INTERCEPTION.code,
                    OptaEventType.TACKLE_EFFECTIVE.code
            );
            case PERDIDAS_BALON: return ImmutableList.of(
                    OptaEventType.DISPOSSESSED.code,
                    OptaEventType.PASS_UNSUCCESSFUL.code,
                    OptaEventType.ERROR.code
            );
            case FALTAS_RECIBIDAS: return ImmutableList.of(
                    OptaEventType.FOUL_RECEIVED.code
            );
            case FALTAS_COMETIDAS: return ImmutableList.of(
                    OptaEventType.FOUL_COMMITTED.code
            );
            case TARJETAS_AMARILLAS: return ImmutableList.of(
                    OptaEventType.YELLOW_CARD.code,
                    OptaEventType.SECOND_YELLOW_CARD.code
            );
            case TARJETAS_ROJAS: return ImmutableList.of(
                    OptaEventType.RED_CARD.code
            );
            case GOLES_ENCAJADOS: return ImmutableList.of(
                    OptaEventType.GOAL_CONCEDED.code
            );
            case PARADAS: return ImmutableList.of(
                    OptaEventType.SAVE_GOALKEEPER.code,
                    OptaEventType.CLAIM.code
            );
            case DESPEJES: return ImmutableList.of(
                    OptaEventType.CLEARANCE.code,
                    OptaEventType.PUNCH.code
            );
            case PENALTIS_DETENIDOS: return ImmutableList.of(
                    OptaEventType.GOALKEEPER_SAVES_PENALTY.code
            );
        }

        throw new RuntimeException(String.format("WTF 7378: StatType: EventType %s unknown", this.id));
    }
}

