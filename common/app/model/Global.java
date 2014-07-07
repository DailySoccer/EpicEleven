package model;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

public class Global {

    static public String currentTimeString() {
        DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        return dateFormat.format(currentTime());

    }

    static public Date currentTime() {
        return fakeTime==null? new Date(): fakeTime;
    }

    public static Date getFakeTime() {
        return fakeTime;
    }

    public static void setFakeTime(Date newFakeTime) {
        fakeTime = newFakeTime;
    }

    private static Date fakeTime;
}
