package model;

import com.fasterxml.jackson.annotation.JsonView;
import java.util.HashMap;

class LiveFantasyPoints {
    public int points;                                          // Puntos totales de un SoccerPlayer

    @JsonView(JsonViews.FullContest.class)
    public HashMap<String, Integer> events = new HashMap<>();   // OptaEventType.name => fantasyPoints conseguidos gracias a el
}