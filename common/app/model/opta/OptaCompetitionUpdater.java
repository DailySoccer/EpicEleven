package model.opta;

import actors.OptaMatchEventChangeProcessor;
import model.GlobalDate;
import org.apache.commons.dbutils.DbUtils;
import org.joda.time.DateTime;
import play.Logger;
import play.db.DB;

import java.sql.*;
import java.util.Date;

public class OptaCompetitionUpdater {

    public OptaCompetitionUpdater(Date startDate, String seasonId, String competitionId) {
        _lastProcessedDate = startDate;
        _seasonIdFilter = seasonId;
        _competitionIdFilter = competitionId;
    }

    public void run() {
        // Para el simulador usamos 1 optaprocesor que nunca reciclamos (pq por dentro cachea)
        if (_optaProcessor == null) {
            _optaProcessor = new OptaProcessor();
        }

        ensureNextDocument(DOCUMENTS_PER_QUERY);

        Date currentDate = GlobalDate.getCurrentDate();

        while (_nextDocDate != null && isBeforeOrEqualNextDocDate(currentDate)) {
            processNextDocument();
            ensureNextDocument(DOCUMENTS_PER_QUERY);
        }
    }

    private boolean isBeforeOrEqualNextDocDate(Date other) {
        return !(new DateTime(_nextDocDate).isAfter(new DateTime(other)));
    }


    private Date getNextDocDate() {
        // Por el sistema de mensajes no se puede pasar null, asi que usamos 0L para señalar que no tenemos siguiente fecha
        return (_nextDocDate != null)? _nextDocDate : new Date(0L);
    }

    private Date getLastProcessedDate() {
        return _lastProcessedDate;
    }

    private void ensureNextDocument(int documentsPerQuery) {

        // Somos una ensure, si el siguiente documento ya esta cargado simplemente retornamos. _nextDoc se pone
        // a null en processNextDocument, a la espera de que se ordene asegurar el siguiente
        if (_nextDocDate != null)
            return;

        ensureConnection();

        try {
            queryNextResultSet(documentsPerQuery);

            if (!readNextDocument()) {
                // Volvemos a intentar leer. Si no hay mas resultados, ahora si, hemos llegado al final.
                queryNextResultSet(documentsPerQuery);

                if (!readNextDocument()) {
                    shutdown();
                }
            }
        }
        catch (Exception e) {
            // Punto de recuperacion 1. Al saltar una excepcion no habremos cambiado _nextDoc == null y por lo tanto reintentaremos.
            // Nota: Podriamos dejarlo fallar y que se produjera un restart del actor. Para ello, lo primero sera cambiar
            //       la estrategia de inicializacion, puesto que en un restart nadie esta poniendo en accion el Tick.
            Logger.error("WTF 1533", e);
        }
    }

    private boolean readNextDocument() throws SQLException {

        // Cuando vamos a leer el siguiente documento, el anterior no puede estar sin procesar.
        if (_nextDocDate != null)
            throw new RuntimeException("WTF 5820");

        if (_optaResultSet.next()) {
            _nextDocDate = new Date(_optaResultSet.getTimestamp("created_at").getTime());
        }

        return _nextDocDate != null;
    }

    private void processNextDocument() {

        // Es posible que ensureNextDocument haya fallado
        if (_nextDocDate == null)
            return;

        try {
            processCurrentDocumentInResultSet(_optaResultSet, _optaProcessor);
            _nextDocDate = null;
        }
        catch (Exception e) {
            // Punto de recuperacion 2. Al saltar una excepcion, no ponemos _nextDoc a null y por lo tanto reintentaremos
            Logger.error("WTF 7817", e);
        }
    }

    private void processCurrentDocumentInResultSet(ResultSet resultSet, OptaProcessor processor) throws SQLException {

        Date created_at = new Date(resultSet.getTimestamp("created_at").getTime());

        String sqlxml = resultSet.getString("xml");
        String name = resultSet.getString("name");
        String feedType = resultSet.getString("feed_type");
        String seasonId = resultSet.getString("season_id");
        String competitionId = resultSet.getString("competition_id");
        String gameId = resultSet.getString("game_id");

        Logger.debug("OptaCompetitionUpdater: {}: {}, {}, {}, {}/{}", _count++, feedType, name, GlobalDate.formatDate(created_at), seasonId, competitionId);

        processor.processOptaDBInput(feedType, name, competitionId, seasonId, gameId, sqlxml);
        new OptaImporter(processor).process();
        new OptaMatchEventChangeProcessor(processor).process();

        _lastProcessedDate = created_at;
    }

    private void queryNextResultSet(int documentsPerQuery) throws SQLException {

        if (_optaResultSet == null || _optaResultSet.isAfterLast()) {

            DbUtils.closeQuietly(null, _stmt, _optaResultSet);
            _stmt = null;
            _optaResultSet = null;

            Date lastProcessedDate = getLastProcessedDate();

            _stmt = _connection.createStatement(ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY);

            String sqlQuery = String.format("SELECT * FROM optaxml " +
                    "WHERE created_at > '%s' AND competition_id = '%s' AND season_id = '%s' " +
                    "ORDER BY created_at LIMIT %d;",
                    new Timestamp(lastProcessedDate.getTime()), _competitionIdFilter, _seasonIdFilter, documentsPerQuery);

            _optaResultSet = _stmt.executeQuery(sqlQuery);
        }
    }

    private void ensureConnection() {
        if (_connection != null) {
            return;
        }
        _connection = DB.getConnection();
    }

    private void shutdown() {
        DbUtils.closeQuietly(_connection, _stmt, _optaResultSet);

        _connection = null;
        _stmt = null;
        _optaResultSet = null;

        _optaProcessor = null;
        _nextDocDate = null;
    }


    final int DOCUMENTS_PER_QUERY = 500;

    Connection _connection;
    ResultSet _optaResultSet;
    Statement _stmt;

    long _count = 0;

    OptaProcessor _optaProcessor;
    Date _nextDocDate;

    String _seasonIdFilter;
    String _competitionIdFilter;

    Date _lastProcessedDate = new Date(0L);
}