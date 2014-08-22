package jobs;

import play.Logger;

import java.util.concurrent.TimeUnit;

public class SampleJob {

    @Schedule(initialDelay = 1, timeUnit = TimeUnit.SECONDS, interval = 2)
    public static void printLog() {
        Logger.debug("Hola");
    }
}
