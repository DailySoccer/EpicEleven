package jobs;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.concurrent.TimeUnit;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface SchedulePolicy {

    public int      initialDelay()  default 0;
    public TimeUnit timeUnit()      default TimeUnit.MILLISECONDS;
    public int      interval()      default 0;  // Por convenio, un intervalo de 0 significa invokar solo vez.
}