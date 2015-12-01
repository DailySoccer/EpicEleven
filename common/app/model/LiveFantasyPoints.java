package model;

import com.fasterxml.jackson.annotation.JsonView;

import java.util.HashMap;
import java.util.List;


public class LiveFantasyPoints {
    public int points;                                          // Puntos totales de un SoccerPlayer

    @JsonView(JsonViews.FullContest.class)
    public HashMap<String, LiveEventInfo> events = new HashMap<>();   // OptaEventType.name => LiveEventInfo

    public long countEvents(List<String> eventsToCount) {
        long result = 0;

        for (String event : eventsToCount) {
            if (events.containsKey(event)) {
                result += events.get(event).count;
            }
        }

        return result;
    }
}