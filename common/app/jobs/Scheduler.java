package jobs;

import org.reflections.Reflections;
import org.reflections.scanners.MethodAnnotationsScanner;
import org.reflections.util.ClasspathHelper;
import org.reflections.util.ConfigurationBuilder;
import play.Logger;
import play.libs.Akka;
import scala.concurrent.duration.Duration;

import java.lang.reflect.Method;
import java.util.Set;
import java.util.concurrent.TimeUnit;

public class Scheduler {

    // Manda a ejecutar a traves de Akka los metodos que esten marcados como "SchedulePolicy" en la lista de namespaces que
    // se le pasen.
    public static void scheduleMethods(String... namespaces) {

        final ConfigurationBuilder configBuilder = build(namespaces);
        final Reflections reflections = new Reflections(configBuilder.setScanners(new MethodAnnotationsScanner()));
        final Set<Method> schedules = reflections.getMethodsAnnotatedWith(SchedulePolicy.class);

        for (final Method schedule : schedules) {
            final SchedulePolicy annotation = schedule.getAnnotation(SchedulePolicy.class);

            final long initialDelay = annotation.initialDelay();
            final TimeUnit timeUnit = annotation.timeUnit();
            final long interval = annotation.interval();

            Akka.system().scheduler().schedule(

                    Duration.apply(initialDelay, timeUnit),
                    Duration.apply(interval, timeUnit),

                    new Runnable() {
                        public void run() {
                            try {
                                schedule.invoke(null);
                            }
                            catch (Exception e) {
                                Logger.error("WTF 0693", e.getCause());
                            }
                        }
                    },

                    Akka.system().dispatcher()
            );

            Logger.info(schedule + " on delay: " + initialDelay + " " + timeUnit + " interval: " + interval + " " + timeUnit);
        }

        if (schedules.isEmpty()) {
            Logger.info("No Schedule methods found");
        }
    }


    private static ConfigurationBuilder build(String... namespaces) {
        final ConfigurationBuilder configBuilder = new ConfigurationBuilder();

        for(final String namespace : namespaces) {
            configBuilder.addUrls(ClasspathHelper.forPackage(namespace));
        }

        return configBuilder;
    }
}