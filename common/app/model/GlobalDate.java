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
        return new DateTime(date).toString(DateTimeFormat.forPattern("yyyy/MM/dd HH:mm:ss").withZoneUTC()) + " UTC";
    }

    static public Date parseDate(String dateStr, String timezone) {

        DateTime dateTime;

        // Si la propia cadena contiene BST, GMT o UTC, es una de las que nos manda Opta en X-Meta-Last-Updated.
        // Con el cambio horario han empezado a mandar fechas con UTC en el X-Meta-Last-Updated: "Sun Oct 26 22:12:58 UTC 2014"
        if (dateStr.contains("BST") || dateStr.contains("GMT") || dateStr.contains("UTC")) {

            if (!dateStr.contains("BST")) {
                dateTime = DateTime.parse(dateStr, DateTimeFormat.forPattern("E MMM dd HH:mm:ss z yyyy"));
            }
            else {
                // Tenemos que eliminar BST pq la interpreta como Bangladesh
                dateTime = DateTime.parse(dateStr.replace("BST ", ""), DateTimeFormat.forPattern("E MMM dd HH:mm:ss yyyy").withZone(_LONDON_TIME_ZONE));
            }
        }
        else {
            if (timezone == null) {
                // Si no nos pasan timezone, asumimos que es una cadena ISO que o bien contendra una TZ o bien vendra siempre
                // en horario del servidor de Londres. A veces vienen con dashes & colons y a veces vienen sin ellos, en
                // basicDateTime format. Sin embargo, no podemos usar basicDateTime pq cuando vienen asi traen un TZ +001 y
                // el parser no se lo traga, asi que usamos un "forPattern".
                if (dateStr.matches(".*:.*:.*"))
                    dateTime = DateTime.parse(dateStr, ISODateTimeFormat.dateTimeParser().withZone(_LONDON_TIME_ZONE));
                else
                    dateTime = DateTime.parse(dateStr, DateTimeFormat.forPattern("yyyyMMdd'T'HHmmssZ").withZone(_LONDON_TIME_ZONE));
            }
            else {
                // Opta manda BST (British Summer Time) o GMT. Tanto BST como GMT son en realidad el horario de Londres, sea verano o no.
                // Si llega una zona horia que no sea BST o GMT, tenemos que revisar pq estamos asumiendo que siempre es asi!
                if (!timezone.equals("BST") && !timezone.equals("GMT"))
                    throw new RuntimeException("WTF 3911: Zona horaria de Opta desconocida. Revisar urgentemente!!! " + timezone);

                dateTime = DateTime.parse(dateStr, DateTimeFormat.forPattern("yyyy-MM-dd HH:mm:ss").withZone(_LONDON_TIME_ZONE));
            }
        }

        // Cuando no viene la zona horaria, asumimos siempre que el servidor esta en Londres
        return dateTime.toDate();
    }

    private static final DateTimeZone _LONDON_TIME_ZONE = DateTimeZone.forID("Europe/London");
    private static Date _fakeDate;
}
