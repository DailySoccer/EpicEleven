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
            templateContest.state = TemplateContest.State.ACTIVE;

            templateContest.instantiate();

            Model.templateContests().update("{_id: #, state: \"OFF\"}", templateContest.templateContestId).with("{$set: {state: \"ACTIVE\"}}");
        }
    }

    public static void onOptaMatchEventIdsChanged(HashSet<String> changedOptaMatchEventIds) {

        for(String optaGameId : changedOptaMatchEventIds) {

            // Buscamos todos los template Match Events asociados con ese partido de Opta
            for (MatchEvent matchEvent : Model.matchEvents().find("{optaMatchEventId: #}", optaGameId).as(MatchEvent.class)) {

                // Los partidos que han terminado no los actualizamos
                if (matchEvent.isFinished()) continue;

                // Ya está marcado como Comenzado?
                boolean matchEventStarted = matchEvent.isStarted();

                // Si NO estaba Comenzado y AHORA SÍ ha comenzado, lo marcamos y lanzamos las acciones de matchEventIsStarted
                if (!matchEventStarted && OptaEvent.isGameStarted(matchEvent.optaMatchEventId)) {
                    matchEvent.setStarted();
                    actionWhenMatchEventIsStarted(matchEvent);
                    matchEventStarted = true;
                }

                // Si ha comenzado, actualizamos la información del "Live"
                if (matchEventStarted) {
                    matchEvent.updateState();

                    // Si HA TERMINADO, lo marcamos y lanzamos las acciones de matchEventIsFinished
                    if (!matchEvent.isFinished() && OptaEvent.isGameFinished(matchEvent.optaMatchEventId)) {
                        matchEvent.setFinished();
                        actionWhenMatchEventIsFinished(matchEvent);
                    }
                }
            }
        }
    }

    private static void actionWhenMatchEventIsStarted(MatchEvent matchEvent) {
        // Los template contests (que incluyan este match event y que esten "activos") tienen que ser marcados como "live"
        Model.templateContests()
                .update("{templateMatchEventIds: {$in:[#]}, state: \"ACTIVE\"}", matchEvent.templateMatchEventId)
                .multi()
                .with("{$set: {state: \"LIVE\"}}");
    }

    private static void actionWhenMatchEventIsFinished(MatchEvent matchEvent) {
        // Buscamos los template contests que incluyan ese partido y que esten en "LIVE"
        Iterable<TemplateContest> templateContests = Model.templateContests().find("{templateMatchEventIds: {$in:[#]}, state: \"LIVE\"}",
                                                                                   matchEvent.templateMatchEventId).as(TemplateContest.class);

        for (TemplateContest templateContest : templateContests) {
            // Si el contest ha terminado (true si todos sus partidos han terminado)
            if (templateContest.isFinished()) {
                Model.templateContests().update("{_id: #, state: \"LIVE\"}", templateContest.templateContestId).with("{$set: {state: \"HISTORY\"}}");

                // Aqui es el único sitio donde se darán los premios
                templateContest.givePrizes();
            }
        }

        matchEvent.saveStats();
    }
}
