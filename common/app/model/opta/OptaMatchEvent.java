package model.opta;

import model.GlobalDate;
import org.jdom2.Element;

import java.util.Date;
import java.util.List;

public class OptaMatchEvent {

    public String optaMatchEventId;
    public String competitionId;
    public String seasonId;
    public String competitionName;
    public String seasonName;
    public String homeTeamId;
    public String awayTeamId;
    public Date matchDate;
    public Date lastModified;
    public boolean dirty = true;

    public OptaMatchEvent() {}

    public OptaMatchEvent(Element myF1, Element matchObject, Element matchInfo) {

        optaMatchEventId = OptaProcessor.getStringId(matchObject, "uID");
        lastModified = GlobalDate.parseDate(matchObject.getAttributeValue("last_modified"), null);

        matchDate = GlobalDate.parseDate(matchInfo.getChild("Date").getContent().get(0).getValue(),
                                         matchInfo.getChild("TZ").getContent().get(0).getValue());

        competitionId = OptaProcessor.getStringId(myF1, "competition_id");
        seasonId = OptaProcessor.getStringId(myF1, "season_id");

        seasonName = myF1.getAttributeValue("season_name");
        competitionName = myF1.getAttributeValue("competition_name");

        List<Element> teams = matchObject.getChildren("TeamData");

        for (Element team : teams) {
            if (team.getAttributeValue("Side").equals("Home")) {
                homeTeamId = OptaProcessor.getStringId(team, "TeamRef");
            }
            else {
                awayTeamId =  OptaProcessor.getStringId(team, "TeamRef");
            }
        }
    }
}
