package jobs;

import play.Logger;
import play.libs.Akka;
import scala.concurrent.duration.Duration;

import java.util.concurrent.TimeUnit;

public class Scheduler {

    static public void eachSecond() {
        Akka.system().scheduler().schedule(
                Duration.create(1, TimeUnit.SECONDS), // A partir del primer segundo
                Duration.create(1, TimeUnit.SECONDS), // Cada segundo
                new Runnable() {
                    public void run() {
                        Logger.debug("Un segundo más");
                        // Aquí vamos metiendo llamadas a funciones que se deban hacer cada segundo
                    }
                },
                Akka.system().dispatcher()
        );
    }

   static public void eachMinute() {
        Akka.system().scheduler().schedule(
                Duration.create(1, TimeUnit.MINUTES), // A partir del primer minuto
                Duration.create(1, TimeUnit.MINUTES), // Cada minuto
                new Runnable() {
                    public void run() {
                        Logger.debug("Un minuto más");
                        // Aquí vamos metiendo llamadas a funciones que se deban hacer cada minuto
                    }
                },
                Akka.system().dispatcher()
        );
    }
}


