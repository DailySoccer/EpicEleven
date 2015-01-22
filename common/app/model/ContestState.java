package model;

public enum ContestState {
    OFF(0),
    ACTIVE(1),
    LIVE(2),
    HISTORY(3),
    CANCELED(4);

    public final int id;

    ContestState(int id) {
        this.id = id;
    }

    public boolean isOff()      { return (this == ContestState.OFF); }
    public boolean isActive()   { return (this == ContestState.ACTIVE); }
    public boolean isLive()     { return (this == ContestState.LIVE); }
    public boolean isHistory()  { return (this == ContestState.HISTORY); }
    public boolean isCanceled() { return (this == ContestState.CANCELED); }
}
