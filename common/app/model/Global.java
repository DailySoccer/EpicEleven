package model;

import java.util.Date;

public class Global {
    static public Date currentTime() {
        return fakeTime==null? new Date(): fakeTime;
    }

    public static Date getFakeTime(){
        return fakeTime;
    }

    public static void setFakeTime(Date newFakeTime){
        fakeTime = newFakeTime;
    }

    private static Date fakeTime;
}
