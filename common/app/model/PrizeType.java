package model;


import java.util.LinkedHashMap;
import java.util.Map;

public enum PrizeType {
    FREE(0),
    WINNER_TAKES_ALL(1),
    TOP_3_GET_PRIZES(2),
    TOP_THIRD_GET_PRIZES(3),
    FIFTY_FIFTY(4),
    STANDARD(-1);

    public final int id;

    PrizeType(int id) {
        this.id = id;
    }
}