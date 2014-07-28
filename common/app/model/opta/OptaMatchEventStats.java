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

    // Constructor por defecto (necesario para Jongo: "unmarshall result to class")
    public OptaMatchEventStats() {

    }

    public OptaMatchEventStats(String optaMatchEventId) {
        this.optaMatchEventId = optaMatchEventId;
    }

    static public OptaMatchEventStats findOne(String optaMatchEventId) {
        return Model.optaMatchEventStats().findOne("{optaMatchEventId: #}", optaMatchEventId).as(OptaMatchEventStats.class);
    }

    public void updateWithTeamData(Element teamData) {
        List<Element> matchPlayers = teamData.getChild("PlayerLineUp").getChildren("MatchPlayer");

        for (Element matchPlayer : matchPlayers) {
           List<Element> elementStats = matchPlayer.getChildren("Stat");
            for (Element stat : elementStats) {
                if (stat.getAttribute("Type").getValue().equals("mins_played")) {
                    String playerId = OptaProcessor.getStringId(matchPlayer, "PlayerRef", "_NO PLAYER ID");
                    int playedMinutes = Integer.parseInt(stat.getContent().get(0).getValue());
                    stats.put(playerId, new OptaPlayerStats(playedMinutes));
                    // Logger.debug("{} - minutes: {}", playerId, playedMinutes);
                }
            }
        }
    }

    public int getPlayedMinutes(String optaPlayerId) {
        OptaPlayerStats playerStats = stats.get(optaPlayerId);
        return (playerStats != null) ? playerStats.playedMinutes : 0;
    }
}

class OptaPlayerStats {
    public int playedMinutes;

    // Constructor por defecto (necesario para Jongo: "unmarshall result to class")
    public OptaPlayerStats() {
    }

    public OptaPlayerStats(int playedMinutes) {
        this.playedMinutes = playedMinutes;
    }
}

