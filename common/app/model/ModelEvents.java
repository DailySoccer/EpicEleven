package model;

import java.util.List;
import java.util.HashSet;
import utils.ListUtils;

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
    public static void instantiateContestsTask() {
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

        if (changedOptaMatchEventIds.isEmpty())
            return;

        for(String optaGameId : changedOptaMatchEventIds) {
            // Logger.info("optaGameId in gameId({})", optaGameId);

            // Buscamos todos los template Match Events asociados con ese partido de Opta
            for (TemplateMatchEvent templateMatchEvent :  Model.templateMatchEvents().find("{optaMatchEventId: #}", optaGameId).as(TemplateMatchEvent.class)) {

                // Existe la version "live" del match event?
                LiveMatchEvent liveMatchEvent = LiveMatchEvent.findFromTemplateMatchEvent(templateMatchEvent);

                // Si no existe y el partido ya ha comenzado, tenemos que crearlo!
                if (liveMatchEvent == null && templateMatchEvent.isStarted()) {
                    liveMatchEvent = LiveMatchEvent.create(templateMatchEvent);
                }

                if (liveMatchEvent != null) {
                    LiveMatchEvent.updateLiveFantasyPoints(liveMatchEvent);

                    // Logger.info("fantasyPoints in liveMatchEvent({})", find.liveMatchEventId);

                    if (templateMatchEvent.isFinished()) {
                        actionWhenMatchEventIsFinished(templateMatchEvent);
                    } else {
                        actionWhenMatchEventIsStarted(templateMatchEvent);
                    }
                }

                // Logger.info("optaGameId in templateMatchEvent({})", find.templateMatchEventId);
            }
        }
    }

    private static void actionWhenMatchEventIsStarted(TemplateMatchEvent templateMatchEvent) {
        // Los template contests (que incluyan este match event y que esten "activos") tendrian que ser marcados como "live"
        Model.templateContests()
                .update("{templateMatchEventIds: {$in:[#]}, state: \"ACTIVE\"}", templateMatchEvent.templateMatchEventId)
                .multi()
                .with("{$set: {state: \"LIVE\"}}");
    }

    private static void actionWhenMatchEventIsFinished(TemplateMatchEvent templateMatchEvent) {
        // Buscamos los template contests que incluyan ese partido y que esten en "LIVE"
        Iterable<TemplateContest> templateContests = Model.templateContests().find("{templateMatchEventIds: {$in:[#]}, state: \"LIVE\"}",
                                                                                    templateMatchEvent.templateMatchEventId).as(TemplateContest.class);

        for (TemplateContest templateContest : templateContests) {
            // Si el contest ha terminado (true si todos sus partidos han terminado)
            if (templateContest.isFinished()) {
                // Cambiar el estado del contest a "HISTORY"
                Model.templateContests().update("{_id: #, state: \"LIVE\"}", templateContest.templateContestId).with("{$set: {state: \"HISTORY\"}}");
            }
        }
    }

    public static void instantiateContests() {
        Iterable<TemplateContest> templateContests = Model.templateContests().find().as(TemplateContest.class);
        for(TemplateContest template : templateContests) {
            template.instantiate();
        }
    }
}
