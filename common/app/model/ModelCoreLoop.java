package model;


import java.util.HashSet;

public class ModelCoreLoop {

    public static void onOptaMatchEventsChanged(HashSet<String> dirtyMatchEvents) {

        if (dirtyMatchEvents.isEmpty())
            return;

        for(String optaGameId : dirtyMatchEvents) {
            // Logger.info("optaGameId in gameId({})", optaGameId);

            // Buscamos todos los template Match Events asociados con ese partido de Opta
            Iterable<TemplateMatchEvent> templateMatchEvents = Model.templateMatchEvents().find("{optaMatchEventId : #}", "g" + optaGameId).as(TemplateMatchEvent.class);
            while(templateMatchEvents.iterator().hasNext()) {
                TemplateMatchEvent templateMatchEvent = templateMatchEvents.iterator().next();

                // Existe la version "live" del match event?
                LiveMatchEvent liveMatchEvent = LiveMatchEvent.find(templateMatchEvent);
                if (liveMatchEvent == null) {
                    // Deberia existir? (true si el partido ha comenzado)
                    if (templateMatchEvent.isStarted()) {
                        liveMatchEvent = LiveMatchEvent.create(templateMatchEvent);
                    }
                }

                if (liveMatchEvent != null) {
                    LiveMatchEvent.updateLiveFantasyPoints(liveMatchEvent);

                    // Logger.info("fantasyPoints in liveMatchEvent({})", find.liveMatchEventId);

                    if (templateMatchEvent.isFinished()) {
                        actionWhenMatchEventIsFinished(templateMatchEvent);
                    }
                    else {
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
