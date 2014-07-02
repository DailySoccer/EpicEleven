package model;


import java.util.LinkedHashMap;
import java.util.Map;

public enum PrizeType {
    STANDARD(0),
    WINNER_TAKES_ALL(1);

    public final int id;

    PrizeType(int id) {
        this.id = id;
    }
}