package actors;

import model.Model;
import model.TemplateContest;
import model.TemplateMatchEvent;
import model.opta.OptaEvent;
import model.opta.OptaProcessor;

import java.util.HashSet;

public class OptaMatchEventChangeProcessor {
    public OptaMatchEventChangeProcessor(OptaProcessor processor) {
        _changedOptaMatchEventIds = processor.getDirtyMatchEventIds();
    }

    public void process() {
        final String TASK_ID = "PROCESS";

        for (String optaGameId : _changedOptaMatchEventIds) {

            // Buscamos todos los template Match Events asociados con ese partido de Opta
            for (TemplateMatchEvent templateMatchEvent : Model.templateMatchEvents().find("{optaMatchEventId: #}", optaGameId).as(TemplateMatchEvent.class)) {

                // Hay alguna tarea pendiente?
                if (!templateMatchEvent.isPending(TASK_ID)) {

                    // Los partidos que han terminado no los actualizamos
                    if (templateMatchEvent.isGameFinished())
                        continue;
                }

                templateMatchEvent.setPending(TASK_ID);

                // Ha comenzado el partido?
                if (OptaEvent.isGameStarted(templateMatchEvent.optaMatchEventId)) {
                    templateMatchEvent.setGameStarted();
                    actionWhenMatchEventIsStarted(templateMatchEvent);

                    // Actualizamos "live"
                    templateMatchEvent.updateState();

                    // Si HA TERMINADO, hacemos las acciones de matchEventIsFinished
                    if (templateMatchEvent.isPostGame() && OptaEvent.isGameFinished(templateMatchEvent.optaMatchEventId)) {
                        templateMatchEvent.setGameFinished();
                        actionWhenMatchEventIsFinished(templateMatchEvent);
                    }
                }

                templateMatchEvent.clearPending(TASK_ID);
            }
        }
    }

    private void actionWhenMatchEventIsStarted(TemplateMatchEvent matchEvent) {
        // Los template contests (que incluyan este match event y que esten "activos") tienen que ser marcados como "live"
        Model.templateContests()
                .update("{templateMatchEventIds: {$in:[#]}, state: \"ACTIVE\"}", matchEvent.templateMatchEventId)
                .multi()
                .with("{$set: {state: \"LIVE\"}}");

        Model.contests()
                .update("{templateMatchEventIds: {$in:[#]}, state: \"ACTIVE\"}", matchEvent.templateMatchEventId)
                .multi()
                .with("{$set: {state: \"LIVE\"}}");
    }

    private void actionWhenMatchEventIsFinished(TemplateMatchEvent matchEvent) {
        // Buscamos los template contests que incluyan ese partido
        Iterable<TemplateContest> templateContests = Model.templateContests()
                .find("{templateMatchEventIds: {$in:[#]}}", matchEvent.templateMatchEventId).as(TemplateContest.class);

        for (TemplateContest templateContest : templateContests) {
            // Si el contest ha terminado (true si todos sus partidos han terminado)
            if (templateContest.isFinished()) {
                Model.templateContests().update("{_id: #, state: \"LIVE\"}", templateContest.templateContestId).with("{$set: {state: \"HISTORY\"}}");

                Model.contests()
                        .update("{templateContestId: #, state: \"LIVE\"}", templateContest.templateContestId)
                        .multi()
                        .with("{$set: {state: \"HISTORY\"}}");
            }
        }

        matchEvent.saveStats();
    }

    private HashSet<String> _changedOptaMatchEventIds;
}
