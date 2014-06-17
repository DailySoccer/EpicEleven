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

    public static Map<String, String> options(){
        LinkedHashMap<String, String> vals = new LinkedHashMap<String, String>();
        for (PrizeType cType : PrizeType.values()) {
            vals.put(cType.name(), cType.name());
        }
        return vals;
    }
}