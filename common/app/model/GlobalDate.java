package model;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;

public class GlobalDate {

    static public String getCurrentDateString() {
        return formatDate(getCurrentDate());
    }

    static public String formatDate(Date date) {
        SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        formatter.setTimeZone(TimeZone.getTimeZone("UTC"));
        return formatter.format(date);
    }

    static public Date getCurrentDate() {
        return _fakeDate == null? new Date(): _fakeDate;
    }

    static public void setFakeDate(Date newFakeDate) {
        _fakeDate = newFakeDate;
    }

    private static Date _fakeDate;
}
