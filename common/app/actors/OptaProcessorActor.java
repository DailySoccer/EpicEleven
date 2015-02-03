package actors;

import akka.actor.UntypedActor;
import model.GlobalDate;
import model.Model;
import model.opta.OptaImporter;
import model.opta.OptaProcessor;
import org.apache.commons.dbutils.DbUtils;
import play.Logger;
import play.db.DB;
import scala.concurrent.duration.Duration;

import java.sql.*;
import java.util.Date;
import java.util.concurrent.TimeUnit;

public class OptaProcessorActor extends UntypedActor {

    public OptaProcessorActor() {
        Logger.debug("OptaProcessorActor preStart");

        // Es posible que se parara justo cuando estaba en isProcessing == true
        resetIsProcessing();

        _nextDocMsg = NextDocMsg.Null();
    }

    // postRestart y preStart se llaman en el nuevo actor (despues de la reinicializacion, claro).
    // preRestart y postStop en el viejo moribundo.
    @Override public void postRestart(Throwable reason) throws Exception {
        Logger.debug("OptaProcessorActor postRestart, reason:", reason);
        super.postRestart(reason);
    }

    @Override public void postStop() {
        Logger.debug("OptaProcessorActor postStop");

        shutdown();
    }

    public void onReceive(Object message) {

        switch ((String)message) {
            case "Tick":
                // Reciclamos memoria (podriamos probar a dejar el cache y reciclar cada cierto tiempo...)
                _optaProcessor = new OptaProcessor();

                ensureNextDocument(REGULAR_DOCUMENTS_PER_QUERY);
                processNextDocument();

                // Reeschudeleamos una llamada a nosotros mismos para el siguiente Tick
                getContext().system().scheduler().scheduleOnce(Duration.create(1, TimeUnit.SECONDS), getSelf(),
                                                               "Tick", getContext().dispatcher(), null);
                break;

            case "SimulatorInit":
                // Puede ser el N-esimo Start, reseteamos nuestro acceso a la DB
                shutdown();

                // Para el simulador usamos 1 optaprocesor que nunca reciclamos
                _optaProcessor = new OptaProcessor();

                // Ensuramos el siguiente, somos asi de amables
                ensureNextDocument(SIMULATOR_DOCUMENTS_PER_QUERY);

                break;

            case "SimulatorShutdown":
                shutdown();
                break;

            case "SimulatorTick":
                ensureNextDocument(SIMULATOR_DOCUMENTS_PER_QUERY);
                processNextDocument();

                // Durante el simulador dejamos el siguiente documento siempre precargado listo para la llamada a GetNextDoc
                ensureNextDocument(SIMULATOR_DOCUMENTS_PER_QUERY);

                break;

            case "GetNextDoc":
                sender().tell(_nextDocMsg, self());
                break;

            default:
                unhandled(message);
                break;
        }
    }

    private static void resetIsProcessing() {
        Model.optaProcessor().update("{stateId: #}", OptaProcessorState.UNIQUE_ID).with("{$set: {isProcessing: false}}");
    }


    public static Date getLastProcessedDate() {
        OptaProcessorState state = OptaProcessorState.findOne();

        if (state != null && state.lastProcessedDate != null) {
            return state.lastProcessedDate;
        }
        else {
            return new Date(0L);
        }
    }

    private void ensureNextDocument(int documentsPerQuery) {

        // Somos una ensure, si el siguiente documento ya esta cargado simplemente retornamos. _nextDocMsg se pone
        // a null en processNextDocument, a la espera de que se ordene asegurar el siguiente
        if (_nextDocMsg.isNotNull())
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
            // Punto de recuperacion 1. Al saltar una excepcion no habremos cambiado _nextDocMsg == null y por lo tanto reintentaremos.
            // Nota: Podriamos dejarlo fallar y que se produjera un restart del actor. Para ello, lo primero sera cambiar
            //       la estrategia de inicializacion, puesto que en un restart nadie esta poniendo en accion el Tick.
            Logger.error("WTF 1533", e);
        }
    }

    private boolean readNextDocument() throws SQLException {

        // Cuando vamos a leer el siguiente documento, el anterior no puede estar sin procesar.
        if (_nextDocMsg.isNotNull())
            throw new RuntimeException("WTF 5820");

        if (_optaResultSet.next()) {
            _nextDocMsg = new NextDocMsg(new Date(_optaResultSet.getTimestamp("created_at").getTime()), _optaResultSet.getInt(1));
        }

        return _nextDocMsg.isNotNull();
    }

    private void processNextDocument() {

        // Es posible que ensureNextDocument haya fallado
        if (_nextDocMsg.isNull())
            return;

        try {
            processCurrentDocumentInResultSet(_optaResultSet, _optaProcessor);
            _nextDocMsg = NextDocMsg.Null();
        }
        catch (Exception e) {
            // Punto de recuperacion 2. Al saltar una excepcion, no ponemos _nextDocMsg a null y por lo tanto reintentaremos
            Logger.error("WTF 7817", e);

            // Aseguramos que podemos reintentar
            resetIsProcessing();
        }
    }

    static private void processCurrentDocumentInResultSet(ResultSet resultSet, OptaProcessor processor) throws SQLException {

        // Evitamos que se produzcan actualizaciones simultaneas
        OptaProcessorState state = Model.optaProcessor().findAndModify("{stateId: #}", OptaProcessorState.UNIQUE_ID)
                                                        .upsert()
                                                        .with("{$set: {isProcessing: true}}")
                                                        .as(OptaProcessorState.class);

        if (state != null && state.isProcessing) {
            throw new RuntimeException("WTF 3885: Colision entre dos actores");
        }

        if (state == null) {
            state = new OptaProcessorState();
            state.lastProcessedDate = new Date(0L);
        }

        Date created_at = new Date(resultSet.getTimestamp("created_at").getTime());

        if (state.lastProcessedDate != null && created_at.before(state.lastProcessedDate))
            throw new RuntimeException("WTF 9190");

        String sqlxml = resultSet.getString("xml");
        String name = resultSet.getString("name");
        String feedType = resultSet.getString("feed_type");
        String seasonId = resultSet.getString("season_id");
        String competitionId = resultSet.getString("competition_id");
        String gameId = resultSet.getString("game_id");

        Logger.debug("OptaProcessorActor: {}, {}, {}, {}/{}", feedType, name, GlobalDate.formatDate(created_at), seasonId, competitionId);

        processor.processOptaDBInput(feedType, name, competitionId, seasonId, gameId, sqlxml);
        new OptaImporter(processor).process();
        new OptaMatchEventChangeProcessor(processor).process();

        state.lastProcessedDate = created_at;
        Model.optaProcessor().update("{stateId: #}", OptaProcessorState.UNIQUE_ID).with(state);
    }

    private void queryNextResultSet(int documentsPerQuery) throws SQLException {

        if (_optaResultSet == null || _optaResultSet.isAfterLast()) {

            DbUtils.closeQuietly(null, _stmt, _optaResultSet);
            _stmt = null;
            _optaResultSet = null;

            Date lastProcessedDate = getLastProcessedDate();

            _stmt = _connection.createStatement(ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY);
            _optaResultSet = _stmt.executeQuery("SELECT * FROM optaxml WHERE created_at > '"
                                                + new Timestamp(lastProcessedDate.getTime()) +
                                                "' ORDER BY created_at LIMIT " + documentsPerQuery + ";");
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
        _nextDocMsg = NextDocMsg.Null();
    }

    final int SIMULATOR_DOCUMENTS_PER_QUERY = 500;
    final int REGULAR_DOCUMENTS_PER_QUERY = 1;

    Connection _connection;
    ResultSet _optaResultSet;
    Statement _stmt;

    OptaProcessor _optaProcessor;
    NextDocMsg _nextDocMsg;


    static private class OptaProcessorState {
        static final String UNIQUE_ID = "--OptaProcessorState--";

        public String stateId = UNIQUE_ID;
        public Date lastProcessedDate;
        public boolean isProcessing;

        static public OptaProcessorState findOne() {
            return Model.optaProcessor().findOne("{stateId: #}", OptaProcessorState.UNIQUE_ID).as(OptaProcessorState.class);
        }
    }

    static public class NextDocMsg {
        final public Date date;
        final public int id;

        public NextDocMsg(Date d, int i) { date = d; id = i; }

        // Como no podemos mandar un mensaje null, lo marcamos asi
        public boolean isNull() { return date == null; }
        public boolean isNotNull() { return date != null; }

        static NextDocMsg Null() { return new NextDocMsg(null, -1); }
    }
}
