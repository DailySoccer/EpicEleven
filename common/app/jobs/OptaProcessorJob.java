package jobs;

import model.GlobalDate;
import model.Model;
import model.ModelEvents;
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

        OptaProcessorState state = Model.optaProcessor().findOne().as(OptaProcessorState.class);

        if (state == null) {
            state = new OptaProcessorState();
            state.lastProcessedDate = new Date(0L);
        }

        try (Connection conn = play.db.DB.getConnection()) {

            ResultSet resultSet = OptaXmlUtils.getNextXmlByDate(conn, state.lastProcessedDate);

            if (resultSet != null && resultSet.next()) {
                processResultSet(resultSet, new OptaProcessor());
            }
        }
        catch (Exception e) {
            Logger.error("WTF 8816", e);
        }
    }

    public static void processResultSet(ResultSet resultSet, OptaProcessor processor) {

        try {
            Date created_at = resultSet.getTimestamp("created_at");

            String sqlxml = resultSet.getString("xml");
            String name = resultSet.getString("name");
            String feedType = resultSet.getString("feed_type");
            String competitionId = resultSet.getString("competition_id");

            Logger.info("OptaProcessorJob: {}, {}, {}, competitionId({})", feedType, name, GlobalDate.formatDate(created_at), competitionId);

            HashSet<String> changedOptaMatchEventIds = processor.processOptaDBInput(feedType, competitionId, sqlxml);
            ModelEvents.onOptaMatchEventIdsChanged(changedOptaMatchEventIds);

            OptaProcessorState state = new OptaProcessorState();
            state.lastProcessedDate = created_at;

            // Si el simulador nos avanza llamando aqui, nosotros registramos correctamente donde estamos. Sin embargo al contrario no es cierto,
            // es decir, si avanzamos a traves del Scheduler el simulador no se entera. Hay una tarea en Asana sobre unificar este estado.
            Model.optaProcessor().update("{stateId: #}", state.stateId).upsert().with(state);
        }
        catch (SQLException e) {
            Logger.error("WTF 7817", e);
        }
    }

    static private class OptaProcessorState {
        public String stateId = "--unique id--";
        public Date lastProcessedDate;
    }
}
