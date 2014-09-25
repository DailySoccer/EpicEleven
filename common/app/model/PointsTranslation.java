package model;

import model.opta.OptaEventType;
import org.bson.types.ObjectId;
import org.jongo.marshall.jackson.oid.Id;
import java.util.Date;

public class PointsTranslation implements JongoId, Initializer {
    @Id
    public ObjectId pointsTranslationId;
    public int eventTypeId;
    public int points;
    public Date timestamp;
    public long unixtimestamp;

    public Date createdAt;

    public PointsTranslation() {
    }

    public void Initialize() {
    }

    public ObjectId getId() {
        return pointsTranslationId;
    }

    public static PointsTranslation findOne(ObjectId pointsTranslationId) {
        return Model.pointsTranslation().findOne("{_id: #}", pointsTranslationId).as(PointsTranslation.class);
    }

    /**
     * Creacion de una entrada de Points Translation
     */
    public static boolean createPointForEvent(int eventType, int points) {
        PointsTranslation pointsTranslation = new PointsTranslation();
        pointsTranslation.eventTypeId = eventType;
        pointsTranslation.points = points;
        pointsTranslation.timestamp = GlobalDate.getCurrentDate();
        pointsTranslation.unixtimestamp = pointsTranslation.timestamp.getTime();
        pointsTranslation.createdAt = GlobalDate.getCurrentDate();
        Model.pointsTranslation().insert(pointsTranslation);
        return true;
    }

    /**
     * Edicion de una entrada de Points Translation
     */
    public static boolean editPointForEvent(ObjectId pointsTranslationId, int points) {
        Model.pointsTranslation().update(pointsTranslationId).with("{$set: {points: #}}", points);
        return true;
    }

    public static void createDefault() {
        int[][] pointsTable = {
                {OptaEventType.PASS_SUCCESSFUL.code, 1},               // pase bien hecho
                {OptaEventType.TAKE_ON.code, 5},                       // regate
                {OptaEventType.FOUL_RECEIVED.code, 3},                 // falta recibida
                {OptaEventType.TACKLE_EFFECTIVE.code, 10},             // recuperacion/entrada con posesion
                {OptaEventType.INTERCEPTION.code, 3},                  // intercepcion
                {OptaEventType.SAVE_GOALKEEPER.code, 10},              // parada
                {OptaEventType.SAVE_PLAYER.code, 10},                  // tiro bloqueado por jugador
                {OptaEventType.CLAIM.code, 10},                        // captura balon
                {OptaEventType.CLEARANCE.code, 5},                     // parada
                {OptaEventType.MISS.code, 5},                          // tiro a puerta
                {OptaEventType.POST.code, 5},                          // tiro a puerta
                {OptaEventType.ATTEMPT_SAVED.code, 7},                 // tiro a puerta
                {OptaEventType.ASSIST.code, 6},                        // asistencia
                //{16, 100},                                           // gol
                {OptaEventType.GOAL_SCORED_BY_GOALKEEPER.code, 75},    // gol del portero
                {OptaEventType.GOAL_SCORED_BY_DEFENDER.code, 65},      // gol del defensa
                {OptaEventType.GOAL_SCORED_BY_MIDFIELDER.code, 50},    // gol del medio
                {OptaEventType.GOAL_SCORED_BY_FORWARD.code, 50},       // gol del delantero
                {OptaEventType.OWN_GOAL.code, -10},                    // gol en contra
                {OptaEventType.YELLOW_CARD.code, -20},                 // tarjeta amarilla
                {OptaEventType.SECOND_YELLOW_CARD.code, -20},          // tarjeta amarilla (segunda)
                {OptaEventType.PUNCH.code, 5},                         // despeje puños
                {OptaEventType.PASS_UNSUCCESSFUL.code, -2},            // perdida de balon, pase perdido
                {OptaEventType.DISPOSSESSED.code, -5},                 // perdida de balon
                {OptaEventType.ERROR.code, -20},                       // perdida de balon
                {OptaEventType.CAUGHT_OFFSIDE.code, -5},               // fuera de juego
                {OptaEventType.FOUL_COMMITTED.code, -5},               // falta infligida
                {OptaEventType.RED_CARD.code, -50},                    // tarjeta roja
                {OptaEventType.PENALTY_COMMITTED.code, -20},           // penalty infligido
                {OptaEventType.PENALTY_FAILED.code, -30},              // penalty fallado
                {OptaEventType.GOALKEEPER_SAVES_PENALTY.code, 30},     // penalty parado por el portero
                {OptaEventType.CLEAN_SHEET.code, 30},                  // clean sheet
                {OptaEventType.GOAL_CONCEDED.code, -10},               // Gol al defensa
        };

        for (int i = 0; i < pointsTable.length; i++) {
            PointsTranslation myPointsTranslation = new PointsTranslation();

            myPointsTranslation.eventTypeId = pointsTable[i][0];

            PointsTranslation pointsTranslation = Model.pointsTranslation().findOne("{eventTypeId: #}", myPointsTranslation.eventTypeId).as(PointsTranslation.class);

            if (pointsTranslation == null) {
                myPointsTranslation.unixtimestamp = 0L;
                myPointsTranslation.timestamp = new Date(myPointsTranslation.unixtimestamp);
                myPointsTranslation.points = pointsTable[i][1];
                myPointsTranslation.createdAt = GlobalDate.getCurrentDate();
                Model.pointsTranslation().insert(myPointsTranslation);
            }
        }
    }
}
