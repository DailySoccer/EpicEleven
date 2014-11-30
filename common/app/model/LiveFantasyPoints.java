package model;

import com.fasterxml.jackson.annotation.JsonView;
import java.util.HashMap;


public class LiveFantasyPoints {
    public int points;                                          // Puntos totales de un SoccerPlayer

    @JsonView(JsonViews.FullContest.class)
    public HashMap<String, LiveEventInfo> events = new HashMap<>();   // OptaEventType.name => LiveEventInfo
}