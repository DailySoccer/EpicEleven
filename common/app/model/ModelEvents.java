package model;

import model.opta.OptaEvent;
import utils.ListUtils;

import java.util.HashSet;
import java.util.List;

public class ModelEvents {

    /**
     *  Tarea que se ejecutará periódicamente
     */
    public static void runTasks() {
        instantiateContestsTask();
    }

    /**
     * Instanciar los Contests necesarios
     *
     * Condiciones:
     * - los template contests que esten apagados y cuya fecha de activacion sean validas
     */
    private static void instantiateContestsTask() {
        Iterable<TemplateContest> templateContestsResults = Model.templateContests()
                .find("{state: \"OFF\", activationAt: {$lte: #}}", GlobalDate.getCurrentDate())
                .as(TemplateContest.class);
        List<TemplateContest> templateContestsOff = ListUtils.asList(templateContestsResults);

        for (TemplateContest templateContest : templateContestsOff) {
            templateContest.state = ContestState.ACTIVE;

            templateContest.instantiate();

            Model.templateContests().update("{_id: #, state: \"OFF\"}", templateContest.templateContestId).with("{$set: {state: \"ACTIVE\"}}");
        }
    }
}
