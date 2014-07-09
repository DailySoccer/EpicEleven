package model;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

public class GlobalDate {

    static public String getCurrentDateString() {
        DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        return dateFormat.format(getCurrentDate());
    }

    static public Date getCurrentDate() {
        return _fakeDate == null? new Date(): _fakeDate;
    }

    public static void setFakeDate(Date newFakeDate) {
        _fakeDate = newFakeDate;
    }

    private static Date _fakeDate;
}
