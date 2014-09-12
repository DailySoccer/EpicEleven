package jobs;

import play.Logger;
import java.util.concurrent.TimeUnit;

public class SampleJob {

    @SchedulePolicy(initialDelay = 0, timeUnit = TimeUnit.SECONDS, interval = 10)
    public static void printLog() {
        Logger.debug("Hello from SampleJob.printLog");
    }
}
