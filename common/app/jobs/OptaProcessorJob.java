package jobs;

import model.GlobalDate;
import model.Model;
import model.ModelEvents;
import model.opta.OptaCompetition;
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

    @SchedulePolicy(initialDelay = 0, timeUnit = TimeUnit.SECONDS, interval = 1)
    public static void checkAndProcessNextOptaXml() {

        // Evitamos que se produzcan actualizaciones simultaneas, por si en algun momento nos equivocamos y lanzamos
        // este worker process varias veces.
        OptaProcessorState state = Model.optaProcessor().findAndModify("{stateId: #}", OptaProcessorState.UNIQUE_ID)
                                                        .upsert()
                                                        .with("{$set: {isProcessing: true}}")
                                                        .as(OptaProcessorState.class);
        if (state == null) {
            state = new OptaProcessorState();
            state.lastProcessedDate = new Date(0L);
        }
        else
        if (state.isProcessing) {
            throw new RuntimeException("WTF 3885");
        }

        try (Connection conn = play.db.DB.getConnection()) {

            ResultSet resultSet = OptaXmlUtils.getNextXmlByDate(conn, state.lastProcessedDate);

            if (resultSet != null && resultSet.next()) {
                state.lastProcessedDate = processResultSet(resultSet, new OptaProcessor());
            }
        }
        catch (Exception e) {
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

            HashSet<String> changedOptaMatchEventIds = processor.processOptaDBInput(feedType, seasonCompetitionId, sqlxml);
            ModelEvents.onOptaMatchEventIdsChanged(changedOptaMatchEventIds);
        }
        catch (SQLException e) {
            Logger.error("WTF 7817", e);
        }

        return processedDate;
    }

    static private class OptaProcessorState {
        static final String UNIQUE_ID = "--OptaProcessorState--";

        public String stateId = UNIQUE_ID;
        public Date lastProcessedDate;
        public boolean isProcessing;
    }
}
