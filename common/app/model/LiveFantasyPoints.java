package model;

import com.fasterxml.jackson.annotation.JsonView;
import java.util.HashMap;

class LiveEventInfo {
    public Integer count;      // número de veces que se ha producido el evento
    public Integer points;     // fantasyPoints conseguidos gracias al evento

    public LiveEventInfo() {
    }

    public LiveEventInfo(Integer points) {
        this.points = points;
        this.count = 1;
    }

    public void add(LiveEventInfo eventInfo) {
        points += eventInfo.points;
        count += eventInfo.count;
    }
}

class LiveFantasyPoints {
    public int points;                                          // Puntos totales de un SoccerPlayer

    @JsonView(JsonViews.FullContest.class)
    public HashMap<String, LiveEventInfo> events = new HashMap<>();   // OptaEventType.name => LiveEventInfo
}