package jobs;

import model.*;
import model.opta.OptaCompetition;
import model.opta.OptaEvent;
import model.opta.OptaProcessor;
import model.opta.OptaXmlUtils;
import play.Logger;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Date;
import java.util.HashSet;
import java.util.concurrent.TimeUnit;

public class OptaProcessorJob {

    @SchedulePolicy(initialDelay = 0, timeUnit = TimeUnit.SECONDS, interval = 0)
    public static void init() {
        OptaProcessorState state = Model.optaProcessor().findOne("{stateId: #}", OptaProcessorState.UNIQUE_ID).as(OptaProcessorState.class);

        try {
            if (state != null && state.isProcessing) {
                Logger.error("WTF 0263: Se ha detectado isProcessing == true durante la inicializacion. Esperamos X segundos para reparar!");

                Scheduler.invokeOnce(20, TimeUnit.SECONDS, OptaProcessorJob.class.getMethod("initAfterDelay", Date.class), state.lastProcessedDate);
            }
            else {
                Scheduler.scheduleMethod(0, 1, TimeUnit.SECONDS, OptaProcessorJob.class.getMethod("checkAndProcessNextOptaXml"));
            }
        }
        catch (NoSuchMethodException e) {
            throw new RuntimeException("WTF 1102");
        }
    }

    public static void initAfterDelay(Date prevLastProcessedDate) {
        OptaProcessorState state = Model.optaProcessor().findOne("{stateId: #}", OptaProcessorState.UNIQUE_ID).as(OptaProcessorState.class);

        // Chequeamos que no hay dos worker process
        if (state == null || !state.isProcessing || !state.lastProcessedDate.equals(prevLastProcessedDate)) {
            throw new RuntimeException("WTF 0003: Este worker process se lanzo mientras habia otro procesando");
        }

        // La fecha coincide con la de hace X segundos. Asumimos que el worker process que la dejo asi esta muerto.
        Model.optaProcessor().update("{stateId: #}", OptaProcessorState.UNIQUE_ID).with("{$set: {isProcessing: false}}");
        Logger.info("WTF 0263: isProcessing == true reparado");

        try {
            Scheduler.scheduleMethod(0, 1, TimeUnit.SECONDS, OptaProcessorJob.class.getMethod("checkAndProcessNextOptaXml"));
        }
        catch (NoSuchMethodException e) {
            throw new RuntimeException("WTF 5112");
        }
    }

    public static void checkAndProcessNextOptaXml() {

        // Evitamos que se produzcan actualizaciones simultaneas, por si en algun momento nos equivocamos y lanzamos
        // este worker process varias veces.
        OptaProcessorState state = Model.optaProcessor().findAndModify("{stateId: #}", OptaProcessorState.UNIQUE_ID)
                                                        .upsert()
                                                        .with("{$set: {isProcessing: true}}")
                                                        .as(OptaProcessorState.class);

        if (state != null && state.isProcessing) {
            Logger.error("WTF 3885: Colision entre dos worker processes");
            return;
        }

        if (state == null) {
            state = new OptaProcessorState();
            state.lastProcessedDate = new Date(0L);
        }

        try (Connection conn = play.db.DB.getConnection()) {

            ResultSet resultSet = OptaXmlUtils.getNextXmlByDate(conn, state.lastProcessedDate);

            if (resultSet != null && resultSet.next()) {
                state.lastProcessedDate = processResultSet(resultSet, new OptaProcessor());
            }
        } catch (Exception e) {
            Logger.error("WTF 8816", e);
        }

        Model.optaProcessor().update("{stateId: #}", OptaProcessorState.UNIQUE_ID).with(state);
    }

    public static Date processResultSet(ResultSet resultSet, OptaProcessor processor) {

        Date processedDate = null;

        try {
            processedDate = resultSet.getTimestamp("created_at");

            String sqlxml = resultSet.getString("xml");
            String name = resultSet.getString("name");
            String feedType = resultSet.getString("feed_type");
            String seasonCompetitionId = OptaCompetition.createId(resultSet.getString("season_id"), resultSet.getString("competition_id"));

            Logger.info("OptaProcessorJob: {}, {}, {}, {}", feedType, name, GlobalDate.formatDate(processedDate), seasonCompetitionId);

            HashSet<String> changedOptaMatchEventIds = processor.processOptaDBInput(feedType, seasonCompetitionId, name, sqlxml);
            onOptaMatchEventIdsChanged(changedOptaMatchEventIds);
        }
        catch (SQLException e) {
            Logger.error("WTF 7817", e);
        }

        return processedDate;
    }

    private static void onOptaMatchEventIdsChanged(HashSet<String> changedOptaMatchEventIds) {

        for (String optaGameId : changedOptaMatchEventIds) {

            // Buscamos todos los template Match Events asociados con ese partido de Opta
            for (MatchEvent matchEvent : Model.matchEvents().find("{optaMatchEventId: #}", optaGameId).as(MatchEvent.class)) {

                // Los partidos que han terminado no los actualizamos
                if (matchEvent.isGameFinished())
                    continue;

                // Ya está marcado como Comenzado?
                boolean matchEventStarted = matchEvent.isGameStarted();

                // Si NO estaba Comenzado y AHORA SÍ ha comenzado, lo marcamos y lanzamos las acciones de matchEventIsStarted
                if (!matchEventStarted && OptaEvent.isGameStarted(matchEvent.optaMatchEventId)) {
                    matchEvent.setGameStarted();
                    actionWhenMatchEventIsStarted(matchEvent);
                    matchEventStarted = true;
                }

                // Si ha comenzado, actualizamos la información del "Live"
                if (matchEventStarted) {
                    matchEvent.updateState();

                    // Si HA TERMINADO, lo marcamos y lanzamos las acciones de matchEventIsFinished
                    if (!matchEvent.isGameFinished() && OptaEvent.isGameFinished(matchEvent.optaMatchEventId)) {
                        matchEvent.setGameFinished();
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

        Model.contests()
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
                Model.contests().update("{templateContestId: #, state: \"LIVE\"}", templateContest.templateContestId).with("{$set: {state: \"HISTORY\"}}");

                // Aqui es el único sitio donde se darán los premios
                templateContest.givePrizes();
            }
        }

        matchEvent.saveStats();
    }

    static private class OptaProcessorState {
        static final String UNIQUE_ID = "--OptaProcessorState--";

        public String stateId = UNIQUE_ID;
        public Date lastProcessedDate;
        public boolean isProcessing;
    }
}
