package model.opta;

import model.Model;
import org.jdom2.DataConversionException;
import org.jdom2.Element;
import play.Logger;

import java.util.HashMap;
import java.util.List;

public class OptaMatchEventStats {

    public String optaMatchEventId;
    public int homeScore;
    public int awayScore;
    public HashMap<String, OptaPlayerStats> stats = new HashMap<>();


    public OptaMatchEventStats() {}

    static public OptaMatchEventStats findOne(String optaMatchEventId) {
        return Model.optaMatchEventStats().findOne("{optaMatchEventId: #}", optaMatchEventId).as(OptaMatchEventStats.class);
    }

    static public void updateTeamStats(String optaMatchEventId, List<Element> teamDatas) {
        HashMap<String, OptaPlayerStats> stats = new HashMap<>();

        for (Element teamData : teamDatas) {

            for (Element matchPlayer : teamData.getChild("PlayerLineUp").getChildren("MatchPlayer")) {

                matchPlayer.getChildren("Stat").forEach(stat -> {
                    if (stat.getAttribute("Type").getValue().equals("mins_played")) {

                        OptaPlayerStats optaPlayerStats = new OptaPlayerStats();
                        optaPlayerStats.playedMinutes = Integer.parseInt(stat.getContent().get(0).getValue());

                        stats.put(OptaProcessor.getStringId(matchPlayer, "PlayerRef"), optaPlayerStats);
                    }
                });
            }
        }

        Model.optaMatchEventStats()
                .update("{optaMatchEventId: #}", optaMatchEventId)
                .upsert()
                .with("{$set: {optaMatchEventId: #, stats: #}}", optaMatchEventId, stats);
    }

    static public void updateMatchResult(String optaMatchEventId, List<Element> teamDatas) {
        int homeScore = -1;
        int awayScore = -1;

        try {
            for (Element teamData : teamDatas) {
                if (teamData.getAttribute("Side").getValue().equalsIgnoreCase("Home")) {
                    homeScore = teamData.getAttribute("Score").getIntValue();
                }
                else if (teamData.getAttribute("Side").getValue().equalsIgnoreCase("Away")) {
                    awayScore = teamData.getAttribute("Score").getIntValue();
                }
            }
        }
        catch (DataConversionException exception) {
            Logger.error("WTF 5566: DataConversionException: optaMatchEventId: {}", optaMatchEventId);
        }

        if (homeScore != -1 || awayScore != -1) {
            Model.optaMatchEventStats()
                    .update("{optaMatchEventId: #}", optaMatchEventId)
                    .upsert()
                    .with("{$set: {optaMatchEventId: #, homeScore: #, awayScore: #}}", optaMatchEventId, homeScore, awayScore);
        }
    }


    public int getPlayedMinutes(String optaPlayerId) {
        OptaPlayerStats playerStats = stats.get(optaPlayerId);
        return (playerStats != null) ? playerStats.playedMinutes : 0;
    }
}
