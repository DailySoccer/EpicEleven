package model;

import com.fasterxml.jackson.annotation.JsonView;
import com.google.common.collect.ImmutableMap;
import model.opta.OptaEventType;
import org.bson.types.ObjectId;
import org.jongo.marshall.jackson.oid.Id;
import utils.ListUtils;

import java.util.Date;
import java.util.List;

public class PointsTranslation implements JongoId, Initializer {
    @Id
    public ObjectId pointsTranslationId;

    @JsonView(JsonViews.NotForClient.class)
    public int eventTypeId;

    public int points;

    @JsonView(JsonViews.NotForClient.class)
    public Date createdAt;
    @JsonView(JsonViews.NotForClient.class)
    public Date lastModified;

    @JsonView(JsonViews.Public.class)
    public String getEventName() {
        return OptaEventType.getEnum(eventTypeId).toString();
    }

    public PointsTranslation() {}
    public void Initialize() {}

    public ObjectId getId() {
        return pointsTranslationId;
    }

    public static PointsTranslation findOne(ObjectId pointsTranslationId) {
        return Model.pointsTranslation().findOne("{_id: #}", pointsTranslationId).as(PointsTranslation.class);
    }

    public static List<PointsTranslation> getAllCurrent() {
        return ListUtils.asList(Model.pointsTranslation()
                        .aggregate("{$match: {createdAt: {$lte: #}}} ", GlobalDate.getCurrentDate())
                        .and("{$sort: {createdAt: -1}}")
                        .and("{ $group: {_id: '$eventTypeId', points: {$first: '$points'}, objectId: {$first: '$_id'}}}")
                        .and("{ $group: {_id: '$objectId', points: {$first: '$points'}, eventTypeId: {$first: '$_id'}}}")
                        .as(PointsTranslation.class));
    }

    /**
     * Creacion de una entrada de Points Translation
     */
    public static boolean createPointForEvent(int eventType, int points) {
        PointsTranslation pointsTranslation = new PointsTranslation();
        pointsTranslation.eventTypeId = eventType;
        pointsTranslation.points = points;
        pointsTranslation.createdAt = GlobalDate.getCurrentDate();
        pointsTranslation.lastModified = pointsTranslation.createdAt;
        Model.pointsTranslation().insert(pointsTranslation);

        OpsLog.onNew(pointsTranslation);
        return true;
    }

    /**
     * Edicion de una entrada de Points Translation
     */
    public static boolean editPointForEvent(ObjectId pointsTranslationId, int points) {
        Model.pointsTranslation().update(pointsTranslationId).with("{$set: {points: #, lastModified: #}}", points, GlobalDate.getCurrentDate());

        OpsLog.onChange(OpsLog.ActingOn.POINTS_TRANSLATION, ImmutableMap.of(
                "pointsTranslationId", pointsTranslationId,
                "points", points));
        return true;
    }

    public static void createDefault() {
        int[][] pointsTable = {
                {OptaEventType.PASS_SUCCESSFUL.code, 1},               // pase bien hecho
                {OptaEventType.TAKE_ON.code, 3},                       // regate
                {OptaEventType.FOUL_RECEIVED.code, 2},                 // falta recibida
                {OptaEventType.TACKLE_EFFECTIVE.code, 4},              // recuperacion/entrada con posesion
                {OptaEventType.INTERCEPTION.code, 3},                  // intercepcion
                {OptaEventType.SAVE_GOALKEEPER.code, 8},               // parada
                {OptaEventType.SAVE_PLAYER.code, 6},                   // tiro bloqueado por jugador
                {OptaEventType.CLAIM.code, 8},                         // captura balon
                {OptaEventType.CLEARANCE.code, 5},                     // parada
                {OptaEventType.MISS.code, 5},                          // tiro a puerta
                {OptaEventType.POST.code, 6},                          // tiro a puerta
                {OptaEventType.ATTEMPT_SAVED.code, 7},                 // tiro a puerta
                {OptaEventType.ASSIST.code, 3},                        // asistencia
                //{16, 100},                                           // gol
                {OptaEventType.GOAL_SCORED_BY_GOALKEEPER.code, 75},    // gol del portero
                {OptaEventType.GOAL_SCORED_BY_DEFENDER.code, 50},      // gol del defensa
                {OptaEventType.GOAL_SCORED_BY_MIDFIELDER.code, 45},    // gol del medio
                {OptaEventType.GOAL_SCORED_BY_FORWARD.code, 40},       // gol del delantero
                {OptaEventType.OWN_GOAL.code, -10},                    // gol en contra
                {OptaEventType.YELLOW_CARD.code, -15},                 // tarjeta amarilla
                {OptaEventType.SECOND_YELLOW_CARD.code, -15},          // tarjeta amarilla (segunda)
                {OptaEventType.PUNCH.code, 6},                         // despeje puños
                {OptaEventType.PASS_UNSUCCESSFUL.code, -2},            // perdida de balon, pase perdido
                {OptaEventType.DISPOSSESSED.code, -3},                 // perdida de balon
                {OptaEventType.ERROR.code, -5},                        // perdida de balon
                {OptaEventType.DECISIVE_ERROR.code, -20},              // perdida de balon
                {OptaEventType.CAUGHT_OFFSIDE.code, -3},               // fuera de juego
                {OptaEventType.FOUL_COMMITTED.code, -4},               // falta infligida
                {OptaEventType.RED_CARD.code, -30},                    // tarjeta roja
                {OptaEventType.PENALTY_COMMITTED.code, -15},           // penalty infligido
                {OptaEventType.PENALTY_FAILED.code, -20},              // penalty fallado
                {OptaEventType.GOALKEEPER_SAVES_PENALTY.code, 30},     // penalty parado por el portero
                {OptaEventType.CLEAN_SHEET.code, 20},                  // clean sheet
                {OptaEventType.GOAL_CONCEDED.code, -10},               // Gol al defensa
        };

        for (int i = 0; i < pointsTable.length; i++) {
            PointsTranslation myPointsTranslation = new PointsTranslation();

            myPointsTranslation.eventTypeId = pointsTable[i][0];

            PointsTranslation pointsTranslation = Model.pointsTranslation().findOne("{eventTypeId: #}", myPointsTranslation.eventTypeId).as(PointsTranslation.class);

            if (pointsTranslation == null) {
                myPointsTranslation.createdAt = new Date(0L);
                myPointsTranslation.points = pointsTable[i][1];
                myPointsTranslation.lastModified = GlobalDate.getCurrentDate();
                Model.pointsTranslation().insert(myPointsTranslation);
            }
        }
    }
}
