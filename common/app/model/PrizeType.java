package model;


import java.util.LinkedHashMap;
import java.util.Map;

public enum PrizeType {
    WINNER_TAKES_ALL(0),
    TOP_3_GET_PRIZES(1),
    TOP_THIRD_GET_PRIZES(2),
    FIFTY_FIFTY(3),
    STANDARD(-1);

    public final int id;

    PrizeType(int id) {
        this.id = id;
    }
}