package model;

import model.opta.OptaEventType;
import org.bson.types.ObjectId;

public class SimulationEvent {
    public int homeScore;
    public int awayScore;
    public int min;
    public int sec;
    public ObjectId templateSoccerPlayerId;
    public OptaEventType eventType;
    public int points;

    public SimulationEvent() {}
}


