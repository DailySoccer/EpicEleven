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

        // Los Contests válidos pasarán a "live"
        // Válido = (Gratuitos AND entries > 1) OR Llenos
        Model.contests()
                .update("{templateMatchEventIds: {$in:[#]}, state: \"ACTIVE\"," +
                        "$or: [" +
                        "  {prizeType: {$eq: \"FREE\"}, \"contestEntries.1\": {$exists: true}}," +
                        "  {freeSlots: 0}" +
                        "]}", matchEvent.templateMatchEventId)
                .multi()
                .with("{$set: {state: \"LIVE\", startedAt: #}}", GlobalDate.getCurrentDate());

        try {
            // Cancelamos aquellos contests que aún permanezcan activos
            //  deben ser los inválidos, dado que en la query anterior se han cambiado de estado a los válidos
            for (Contest contest : Contest.findAllActiveFromTemplateMatchEvent(matchEvent.templateMatchEventId)) {
                Job job = CancelContestJob.create(contest.contestId);
                if (!job.isDone()) {
                    Logger.error("CancelContestJob {} error", contest.contestId);
                }
            }
        }
        catch(DuplicateKeyException e) {
            play.Logger.error("WTF 3333: actionWhenMatchEventIsStarted: {}", e.toString());
        }
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
                        .with("{$set: {state: \"HISTORY\", finishedAt: #}}", GlobalDate.getCurrentDate());
            }
        }

        matchEvent.saveStats();
    }

    private HashSet<String> _changedOptaMatchEventIds;
}
