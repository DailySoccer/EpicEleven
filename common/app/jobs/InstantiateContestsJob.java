package jobs;

import model.GlobalDate;
import model.TemplateContest;
import play.Logger;

import java.util.List;
import java.util.concurrent.TimeUnit;

public class InstantiateContestsJob {

    /**
     * Instanciar los Contests necesarios
     *
     * Condiciones:
     * - los template contests que esten apagados y cuya fecha de activacion sean validas
     */
    @SchedulePolicy(initialDelay = 0, timeUnit = TimeUnit.SECONDS, interval = 5)
    public static void instantiateContestsTask() {

        Logger.info("instantiateContestsTask: {}", GlobalDate.getCurrentDate());

        List<TemplateContest> templateContestsOff = TemplateContest.findAllByActivationAt(GlobalDate.getCurrentDate());

        for (TemplateContest templateContest : templateContestsOff) {

            // El TemplateContest instanciara sus Contests y MatchEvents asociados
            templateContest.instantiate();
        }
    }
}
