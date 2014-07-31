package model;

import com.google.common.collect.ImmutableList;
import model.opta.*;
import play.Logger;

import java.util.*;

public class SoccerPlayerStats {

    public String optaPlayerId;
    public String optaMatchEventId;

    public int fantasyPoints;
    public int playedMinutes;
    public HashMap<String, Integer> events = new HashMap<>();

    public SoccerPlayerStats() { }

    public SoccerPlayerStats(String optaPlayerId, String optaMatchEventId, int fantasyPoints) {
        this.optaMatchEventId = optaMatchEventId;
        this.optaPlayerId = optaPlayerId;
        this.fantasyPoints = fantasyPoints;
    }

    public int getStat(SoccerPlayerStatType statType) {
        if (events.containsKey(statType.toString())) {
            return events.get(statType.toString());
        }
        return 0;
    }

    public void updateStats() {
        // Logger.debug("Stats: {} -----", optaPlayerId);

        OptaMatchEventStats matchEventStats = OptaMatchEventStats.findOne(optaMatchEventId);
        playedMinutes = (matchEventStats != null) ? matchEventStats.getPlayedMinutes(optaPlayerId) : 0;

        // Registrar los eventos "acumulados"
        for (SoccerPlayerStatType statType : SoccerPlayerStatType.values()) {
            int count = countStat(statType);
            if (count > 0) {
                events.put(statType.toString(), count);

                // Logger.debug("Stat: {} : {} count", statType, count);
            }
        }
    }

    private int countStat(SoccerPlayerStatType statType) {
        int count = 0;

        List<Integer> typeIds = statType.getEventTypes();
        if (typeIds != null) {
            count = (int) Model.optaEvents().count("{optaPlayerId: #, gameId: #, typeId:  {$in: #}}", optaPlayerId, optaMatchEventId, typeIds);
        }

        return count;
    }
}
