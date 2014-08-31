package model.opta;

import java.util.Date;

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

    public OptaMatchEvent(){}
}
