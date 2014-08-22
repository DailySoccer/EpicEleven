package jobs;

import org.reflections.Reflections;
import org.reflections.scanners.MethodAnnotationsScanner;
import org.reflections.util.ClasspathHelper;
import org.reflections.util.ConfigurationBuilder;
import play.Logger;
import play.libs.Akka;
import scala.concurrent.duration.Duration;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Set;
import java.util.concurrent.TimeUnit;

public class Scheduler {

    @SuppressWarnings("unchecked")
    public static void scheduleMethods(String... namespaces) {

        final ConfigurationBuilder configBuilder = build(namespaces);
        final Reflections reflections = new Reflections(configBuilder.setScanners(new MethodAnnotationsScanner()));
        final Set<Method> schedules = reflections.getMethodsAnnotatedWith(Schedule.class);
        if(!schedules.isEmpty()) {
            Logger.debug("Scheduling methods:");
        }
        for(final Method schedule : schedules) {
            final Schedule annotation = schedule.getAnnotation(Schedule.class);

            long initialDelay = annotation.initialDelay();
            TimeUnit timeUnitInitial = annotation.timeUnit();
            long interval = annotation.interval();
            TimeUnit timeUnitInterval = annotation.timeUnit();

            Akka.system().scheduler().schedule(
                    Duration.apply(initialDelay, timeUnitInterval),
                    Duration.apply(interval, timeUnitInterval),
                    new Runnable() {
                        public void run() {
                            try {
                                schedule.invoke(null);
                            } catch (IllegalAccessException e) {
                                Logger.error("WTF 7472", e);
                            } catch (InvocationTargetException e) {
                                Logger.error("WTF 7473", e);
                            }
                        }
                    },
                    Akka.system().dispatcher()
            );
            Logger.debug(schedule + " on delay: " + initialDelay + " " + timeUnitInitial + " interval: " + interval + " " + timeUnitInterval);
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