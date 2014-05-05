package model;

import com.fasterxml.jackson.annotation.JsonView;
import org.bson.types.ObjectId;

import java.util.Date;
import java.util.List;

public class Contest {

    public enum PrizeType {
        STANDARD,
        WINNER_TAKES_ALL
    }

    public String name;
    public int salaryCap;

    public int currentEntries;
    public int maxEntries;

    public int entryFee;
    public PrizeType prizeType;

    List<MatchEvent> matchEvents;
}
