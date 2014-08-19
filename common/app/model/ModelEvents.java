package model;

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

        if (changedOptaMatchEventIds==null || changedOptaMatchEventIds.isEmpty())
            return;

        for(String optaGameId : changedOptaMatchEventIds) {
            // Logger.info("optaGameId in gameId({})", optaGameId);

            // Buscamos todos los template Match Events asociados con ese partido de Opta
            for (MatchEvent matchEvent : Model.matchEvents().find("{optaMatchEventId: #}", optaGameId).as(MatchEvent.class)) {

                if (matchEvent.isStarted()) {
                    matchEvent.updateState();
                    Contest.updateRanking(matchEvent.templateMatchEventId);

                    if (matchEvent.isFinished()) {
                        actionWhenMatchEventIsFinished(matchEvent);
                    } else {
                        actionWhenMatchEventIsStarted(matchEvent);
                    }
                }

                // Logger.info("optaGameId in templateMatchEvent({})", find.templateMatchEventId);
            }
        }
    }

    private static void actionWhenMatchEventIsStarted(MatchEvent matchEvent) {
        // Los template contests (que incluyan este match event y que esten "activos") tendrian que ser marcados como "live"
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
                // Cambiar el estado del contest a "HISTORY"
                Model.templateContests().update("{_id: #, state: \"LIVE\"}", templateContest.templateContestId).with("{$set: {state: \"HISTORY\"}}");

                // TODO: Se tendría que hacer cuando el TemplateContest se marque como CLOSED
                templateContest.setClosed();
            }
        }

        matchEvent.saveStats();
    }
}
