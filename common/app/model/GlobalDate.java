package model;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.ISODateTimeFormat;
import java.util.Date;

public class GlobalDate {

    static public Date getCurrentDate() {
        return _fakeDate == null? new Date() : _fakeDate;
    }

    static public void setFakeDate(Date newFakeDate) {
        _fakeDate = newFakeDate;
    }

    static public String getCurrentDateString() {
        return formatDate(getCurrentDate());
    }

    // Para mostrar fechas en sitios como el log o la zona de administracion, siempre tenemos que llamar aqui
    static public String formatDate(Date date) {
        return new DateTime(date).toString(DateTimeFormat.mediumDateTime().withZoneUTC()) + " UTC";
    }

    static public Date parseDate(String dateStr, String timezone) {

        DateTime dateTime;

        // Si la propia cadena contiene BST o GMT, es una de las que nos manda Opta en X-Meta-Last-Updated
        if (dateStr.contains("BST") || dateStr.contains("GMT")) {
            dateTime = DateTime.parse(dateStr.replace("BST ", "").replace("GMT ", ""),
                                      DateTimeFormat.forPattern("E MMM dd HH:mm:ss yyyy").
                                      withZone(DateTimeZone.forID("Europe/London")));
        }
        else {
            // Si no nos pasan timezone, asumimos que es una cadena ISO que o bien contendra una TZ o bien vendra siempre
            // en horario del servidor de Londres
            if (timezone == null) {
                dateTime = DateTime.parse(dateStr, ISODateTimeFormat.dateTimeParser().withZone(DateTimeZone.forID("Europe/London")));
            }
            else {
                // Opta manda BST (British Summer Time) o GMT. Tanto BST como GMT son en realidad el horario de Londres, sea verano o no.
                // Si llega una zona horia que no sea BST o GMT, tenemos que revisar pq estamos asumiendo que siempre es asi!
                if (!timezone.equals("BST") && !timezone.equals("GMT"))
                    throw new RuntimeException("WTF 3911: Zona horaria de Opta desconocida. Revisar urgentemente!!! " + timezone);

                dateTime = DateTime.parse(dateStr, DateTimeFormat.forPattern("yyyy-MM-dd HH:mm:ss").withZone(DateTimeZone.forID("Europe/London")));
            }
        }

        return dateTime.toDate();
    }

    private static Date _fakeDate;
}
