package model.opta;

import java.util.LinkedHashMap;
import java.util.Map;


public enum OptaEventType {
    PASS_SUCCESSFUL         (1001, "Any pass successful from one player to another."),
    PASS_UNSUCCESSFUL       (1002, "Any pass attempted from one player to a wrong place."),
    TAKE_ON                 (3, "Attempted dribble past an opponent" ),
    FOUL_RECEIVED           (4, "Player who was fouled"),
    TACKLE                  (7, "Tackle: dispossesses an opponent of the ball, not retaining possession"),
    INTERCEPTION            (8, "When a player intercepts any pass event between opposition players and prevents the ball reaching its target"),
    SAVE                    (10, "Goalkeeper saves a shot on goal."),
    CLAIM                   (11, "Goalkeeper catches a crossed ball"),
    CLEARANCE               (12, "Player under pressure hits ball clear of the defensive zone or/and out of play"),
    MISS                    (13, "Shot on goal which goes wide over the goal"),
    POST                    (14, "The ball hits the frame of the goal"),
    ATTEMPT_SAVED           (15, "Shot saved, event for the player who shot the ball"),
    YELLOW_CARD             (17, "Yellow card shown to player"),
    PUNCH                   (41, "Ball is punched clear by Goalkeeper"),
    DISPOSSESSED            (50, "Player is successfully tacked and loses possession of the ball"),
    ERROR                   (51, "Mistake by player losing the ball"),
    //CAUGHT_OFFSIDE          (72, "Player who is offside"),
    ASSIST                  (1210, "The pass was an assist for a shot"),
    TACKLE_EFFECTIVE        (1007, "Tackle: dispossesses an opponent of the ball, retaining possession"),
    GOAL_SCORED_BY_GOALKEEPER   (1601, "Goal scored by the goalkeeper"),
    GOAL_SCORED_BY_DEFENDER     (1602, "Goal scored by a defender"),
    GOAL_SCORED_BY_MIDFIELDER   (1603, "Goal scored by a midfielder"),
    GOAL_SCORED_BY_FORWARD      (1604, "Goal scored by a forward"),
    OWN_GOAL                (1699, "Own goal scored by the player"),
    FOUL_COMMITTED          (1004, "Player who committed the foul"),
    SECOND_YELLOW_CARD      (1017, "Second yellow card shown to player"),
    RED_CARD                (1117, "Red card shown to player"),
    CAUGHT_OFFSIDE          (1072, "Player who is offside"),
    PENALTY_COMMITTED       (1409, "Player who committed the foul (penalty)"),
    PENALTY_FAILED          (1410, "Player who shots penalty and fails"),
    GOALKEEPER_SAVES_PENALTY(1458, "Goalkeeper saves a penalty shot"),
    CLEAN_SHEET             (2000, "Clean sheet: More than 60 min played without conceding any goal"),
    GOAL_CONCEDED           (2001, "Goal conceded while player is on the field"),
    _INVALID_               (9999, "Clean sheet: More than 60 min played without conceding any goal");

    public final int code;
    public final String description;

    OptaEventType(int c, String desc) {
        code = c;
        description = desc;
    }

    public static OptaEventType getEnum(int c) {
        for (OptaEventType optaEventType : OptaEventType.values()){
            if (optaEventType.code == c) {
                return optaEventType;
            }
        }
        return _INVALID_;
    }

    public static Map<String, String> options() {
        LinkedHashMap<String, String> vals = new LinkedHashMap<String, String>();
        for (OptaEventType eType : OptaEventType.values()) {
            if (!eType.equals(OptaEventType._INVALID_)){
                vals.put(eType.name(), eType.name().concat(": ".concat(eType.description)));
            }
        }
        return vals;
    }
}
