package actors;

import com.mongodb.DuplicateKeyException;
import model.*;
import model.jobs.CancelContestJob;
import model.jobs.Job;
import model.opta.OptaEvent;
import model.opta.OptaProcessor;
import play.Logger;

import java.util.HashSet;

public class OptaMatchEventChangeProcessor {
    public OptaMatchEventChangeProcessor(OptaProcessor processor) {
        _changedOptaMatchEventIds = processor.getDirtyMatchEventIds();
    }

    public OptaMatchEventChangeProcessor(String optaMatchEventId) {
        _changedOptaMatchEventIds = new HashSet<String>();
        _changedOptaMatchEventIds.add(optaMatchEventId);
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
                    actionWhenMatchEventUpdated(templateMatchEvent);

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
        TemplateContest.actionWhenMatchEventIsStarted(matchEvent);
    }

    private void actionWhenMatchEventUpdated(TemplateMatchEvent matchEvent) {
        TemplateContest.actionWhenMatchEventUpdated(matchEvent);
    }

    private void actionWhenMatchEventIsFinished(TemplateMatchEvent matchEvent) {
        TemplateContest.actionWhenMatchEventIsFinished(matchEvent);
        matchEvent.saveStats();
    }

    private HashSet<String> _changedOptaMatchEventIds;
}
