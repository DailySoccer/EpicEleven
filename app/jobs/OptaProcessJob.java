package jobs;


import model.ModelEvents;
import model.opta.OptaProcessor;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.HashSet;
import java.util.concurrent.TimeUnit;

public class OptaProcessJob {

    @SchedulePolicy(initialDelay = 0, timeUnit = TimeUnit.SECONDS, interval = 1)
    public static void checkAndProcessNewFile() {





        /*
        HashSet<String> updatedMatchEvents = new OptaProcessor().processOptaDBInput(feedType, fileName, bodyText);
        ModelEvents.onOptaMatchEventIdsChanged(updatedMatchEvents);
        */
    }

}
