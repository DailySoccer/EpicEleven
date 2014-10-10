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
        for (String optaGameId : _changedOptaMatchEventIds) {

            // Buscamos todos los template Match Events asociados con ese partido de Opta
            for (TemplateMatchEvent templateMatchEvent : Model.templateMatchEvents().find("{optaMatchEventId: #}", optaGameId).as(TemplateMatchEvent.class)) {

                // Los partidos que han terminado no los actualizamos
                if (templateMatchEvent.isGameFinished())
                    continue;

                // Ya está marcado como Comenzado?
                boolean matchEventStarted = templateMatchEvent.isGameStarted();

                // Si NO estaba Comenzado y AHORA SÍ ha comenzado, lo marcamos y lanzamos las acciones de matchEventIsStarted
                if (!matchEventStarted && OptaEvent.isGameStarted(templateMatchEvent.optaMatchEventId)) {
                    templateMatchEvent.setGameStarted();
                    actionWhenMatchEventIsStarted(templateMatchEvent);
                    matchEventStarted = true;
                }

                // Si ha comenzado, actualizamos la información del "Live"
                if (matchEventStarted) {
                    templateMatchEvent.updateState();

                    // Si HA TERMINADO, lo marcamos y lanzamos las acciones de matchEventIsFinished
                    if (templateMatchEvent.isPostGame() && !templateMatchEvent.isGameFinished() && OptaEvent.isGameFinished(templateMatchEvent.optaMatchEventId)) {
                        templateMatchEvent.setGameFinished();
                        actionWhenMatchEventIsFinished(templateMatchEvent);
                    }
                }
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
        // Buscamos los template contests que incluyan ese partido y que esten en "LIVE"
        Iterable<TemplateContest> templateContests = Model.templateContests()
                .find("{templateMatchEventIds: {$in:[#]}, state: \"LIVE\"}", matchEvent.templateMatchEventId).as(TemplateContest.class);

        for (TemplateContest templateContest : templateContests) {
            // Si el contest ha terminado (true si todos sus partidos han terminado)
            if (templateContest.isFinished()) {
                Model.templateContests().update("{_id: #, state: \"LIVE\"}", templateContest.templateContestId).with("{$set: {state: \"HISTORY\"}}");

                Model.contests()
                        .update("{templateContestId: #, state: \"LIVE\"}", templateContest.templateContestId)
                        .multi()
                        .with("{$set: {state: \"HISTORY\"}}");

                // Aqui es el único sitio donde se darán los premios
                templateContest.givePrizes();
            }
        }

        matchEvent.saveStats();
    }

    private HashSet<String> _changedOptaMatchEventIds;
}
