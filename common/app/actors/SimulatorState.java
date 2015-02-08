package actors;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import model.GlobalDate;

import java.util.Date;


public class SimulatorState {
    static public final int MAX_SPEED = -1;
    static final String UNIQUE_ID = "--SimulatorState--";

    public String  stateId = UNIQUE_ID;
    public Date    simulationDate;
    public Date    pauseDate;
    public boolean isPaused;
    public int     speedFactor = MAX_SPEED;

    public SimulatorState() { }
    public SimulatorState(SimulatorState o) {
        if (o != null) {
            this.stateId = o.stateId;
            this.simulationDate = o.simulationDate;
            this.pauseDate = o.pauseDate;
            this.isPaused = o.isPaused;
            this.speedFactor = o.speedFactor;
        }
    }

    @JsonSerialize
    public boolean isInit() { return simulationDate != null; }

    @JsonSerialize
    public Date   getCurrentDate() { return GlobalDate.getCurrentDate(); }

    @JsonSerialize
    public String getCurrentDateFormatted() { return GlobalDate.getCurrentDateString(); }
    @JsonSerialize
    public String getSimulationDateFormatted() { return (simulationDate != null)? GlobalDate.formatDate(simulationDate) : ""; }
    @JsonSerialize
    public String getPauseDateFormatted() { return (pauseDate != null)? GlobalDate.formatDate(pauseDate) : ""; }
}
