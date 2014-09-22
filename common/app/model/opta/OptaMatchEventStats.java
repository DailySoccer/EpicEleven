package model.opta;

import model.Model;
import org.jdom2.Element;
import play.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class OptaMatchEventStats {

    public String optaMatchEventId;
    public HashMap<String, OptaPlayerStats> stats = new HashMap<>();


    public OptaMatchEventStats() {}

    public OptaMatchEventStats(String optaMatchEventId, List<Element> teamDatas) {
        this.optaMatchEventId = optaMatchEventId;

        for (Element teamData : teamDatas) {
            
            for (Element matchPlayer : teamData.getChild("PlayerLineUp").getChildren("MatchPlayer")) {

                for (Element stat : matchPlayer.getChildren("Stat")) {
                    if (stat.getAttribute("Type").getValue().equals("mins_played")) {

                        OptaPlayerStats optaPlayerStats = new OptaPlayerStats();
                        optaPlayerStats.playedMinutes = Integer.parseInt(stat.getContent().get(0).getValue());

                        stats.put(OptaProcessor.getStringId(matchPlayer, "PlayerRef"), optaPlayerStats);
                    }
                }
            }
        }
    }

    static public OptaMatchEventStats findOne(String optaMatchEventId) {
        return Model.optaMatchEventStats().findOne("{optaMatchEventId: #}", optaMatchEventId).as(OptaMatchEventStats.class);
    }

    public int getPlayedMinutes(String optaPlayerId) {
        OptaPlayerStats playerStats = stats.get(optaPlayerId);
        return (playerStats != null) ? playerStats.playedMinutes : 0;
    }
}

class OptaPlayerStats {
    public int playedMinutes;

    public OptaPlayerStats() {}
}

