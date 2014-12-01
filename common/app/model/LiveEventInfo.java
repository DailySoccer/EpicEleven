package model;


public class LiveEventInfo {
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
