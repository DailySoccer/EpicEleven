package actors;

import org.bson.types.ObjectId;
import model.*;
import model.jobs.*;
import model.opta.OptaEvent;
import model.opta.OptaProcessor;
import play.Logger;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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
        cancelInvalidContests(matchEvent);

        // Los template contests (que incluyan este match event y que esten "activos") tienen que ser marcados como "live"
        Model.templateContests()
                .update("{templateMatchEventIds: {$in:[#]}, state: \"ACTIVE\"}", matchEvent.templateMatchEventId)
                .multi()
                .with("{$set: {state: \"LIVE\"}}");

        Model.contests()
                .update("{templateMatchEventIds: {$in:[#]}, state: \"ACTIVE\"}", matchEvent.templateMatchEventId)
                .multi()
                .with("{$set: {state: \"LIVE\", contestOpen: #}}", GlobalDate.getCurrentDate());
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
                        .with("{$set: {state: \"HISTORY\", contestClose: #}}", GlobalDate.getCurrentDate());
            }
        }

        matchEvent.saveStats();
    }

    private void cancelInvalidContests(TemplateMatchEvent matchEvent) {
        List<Contest> invalidContests = new ArrayList<>();

        // Buscar aquellos contests que únicamente tengan ninguna o una entrada de usuario
        invalidContests.addAll(Contest.findAllActiveWithNoneOrOneEntry(matchEvent.templateMatchEventId));

        // Buscar aquellos contests que incluyan el partido, que tengan un entryFee y que no estén llenos...
        invalidContests.addAll(Contest.findAllActiveNotFullWithEntryFee(matchEvent.templateMatchEventId));

        Set<ObjectId> canceled = new HashSet<>();
        for (Contest contest: invalidContests) {
            // Evitamos solicitar 2 veces la cancelación de un contest
            if (canceled.contains(contest.contestId)) {
                continue;
            }
            canceled.add(contest.contestId);

            // Crear un job para cancelar el contest
            Job job = CancelContestJob.create(contest.contestId);
            if (!job.isDone()) {
                Logger.error("CancelContestJob {} error", contest.contestId);
            }
        }
    }

    private HashSet<String> _changedOptaMatchEventIds;
}
