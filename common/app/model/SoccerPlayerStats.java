package model;

import com.mongodb.WriteConcern;
import org.bson.types.ObjectId;
import org.jongo.marshall.jackson.oid.Id;
import com.google.common.collect.ImmutableList;
import model.opta.*;
import play.Logger;
import utils.ListUtils;

import java.util.Date;
import java.util.HashMap;
import java.util.List;

public class SoccerPlayerStats {

    public String optaPlayerId;
    public String optaMatchEventId;

    public int fantasyPoints;
    public int playedMinutes;
    public HashMap<Integer, Integer> events = new HashMap<>();

    // Constructor por defecto (necesario para Jongo: "unmarshall result to class")
    public SoccerPlayerStats() {
    }

    public SoccerPlayerStats(String optaPlayerId, String optaMatchEventId, int fantasyPoints) {
        this.optaMatchEventId = optaMatchEventId;
        this.optaPlayerId = optaPlayerId;
        this.fantasyPoints = fantasyPoints;
    }

    public void updateStats() {
        // Registrar los eventos "acumulados"
        for (OptaEventType eType : OptaEventType.values()) {
            int count = (int) Model.optaEvents().count("{optaPlayerId: #, gameId: #, typeId: #}", optaPlayerId, optaMatchEventId, eType.getCode());
            if (count > 0) {
                events.put(eType.getCode(), count);
            }
        }
    }
}
