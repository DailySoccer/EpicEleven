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

    //
    // Ejecuta a traves de Akka los metodos que esten marcados como "SchedulePolicy" en la lista de namespaces que se le pasen.
    //
    public static void scheduleMethods(String... namespaces) {

        final ConfigurationBuilder configBuilder = build(namespaces);
        final Reflections reflections = new Reflections(configBuilder.setScanners(new MethodAnnotationsScanner()));
        final Set<Method> schedules = reflections.getMethodsAnnotatedWith(SchedulePolicy.class);

        for (final Method scheduleMethod : schedules) {
            final SchedulePolicy annotation = scheduleMethod.getAnnotation(SchedulePolicy.class);

            final long initialDelay = annotation.initialDelay();
            final TimeUnit timeUnit = annotation.timeUnit();
            final long interval = annotation.interval();

            // Por convenio, un intervalo de 0 significa invokar solo vez.
            if (interval == 0) {
                invokeOnce(initialDelay, timeUnit, scheduleMethod);
            }
            else {
                scheduleMethod(initialDelay, interval, timeUnit, scheduleMethod);
            }
        }

        if (schedules.isEmpty()) {
            Logger.info("No SchedulePolicy methods found");
        }
    }

    public static void invokeOnce(long initialDelay, TimeUnit timeUnit, final Method scheduleMethod, final Object... args) {

        Logger.debug("invokeOnce: {}, delay: {} {}", scheduleMethod, initialDelay, timeUnit);

        // Cuando no hay initialDelay usamos invokacion directa, lo que nos garantiza que se ejecutan antes que el
        // resto de scheduled methods con interval != 0 & initialDelay == 0.
        if (initialDelay == 0) {
            invoke(scheduleMethod, null, args);
        }
        else {
            Akka.system().scheduler().scheduleOnce(
                Duration.apply(initialDelay, timeUnit),

                new Runnable() {
                    public void run() {
                        invoke(scheduleMethod, null, args);
                    }
                },

                Akka.system().dispatcher()
            );
        }
    }

    public static void scheduleMethod(long initialDelay, long interval, TimeUnit timeUnit, final Method scheduleMethod, final Object... args) {

        Logger.debug("scheduleMethod: {}, delay: {} {}, interval: {} {}", scheduleMethod, initialDelay, timeUnit, interval, timeUnit);

        Akka.system().scheduler().schedule(
            Duration.apply(initialDelay, timeUnit),
            Duration.apply(interval, timeUnit),

            new Runnable() {
                public void run() {
                    invoke(scheduleMethod, args);
                }
            },

            Akka.system().dispatcher()
        );
    }

    private static void invoke(final Method method, final Object obj, final Object... args) {
        try {
            method.invoke(obj, args);
        }
        catch (Exception e) {
            Logger.error("WTF 2019 Exception invoking a scheduled method:", e.getCause());
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