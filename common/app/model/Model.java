package model;

import actors.Actors;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.mongodb.*;
import org.bson.types.ObjectId;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.joda.money.Money;
import org.jongo.*;
import org.jongo.marshall.jackson.JacksonMapper;
import play.Logger;
import play.Play;
import utils.*;

import java.io.IOException;
import java.util.List;


public class Model {
    static public String STATE_ID = "-4/2013"; // 4: Mundial

    // COLLECTIONS QUE SE CONSERVAN
    static public String SESSIONS_NAME = "sessions";
    static public String USERS_NAME = "users";
    static public String ORDERS_NAME = "orders";
    static public String REFUNDS_NAME = "refunds";
    static public String PROMOS_NAME = "promos";
    static public String BONUS_NAME = "bonus";
    static public String ACCOUNTING_TRANSACTIONS_NAME = "accountingTransactions";
    static public String PAYPAL_RESPONSES_NAME = "paypalResponses";
    static public String POINTS_TRANSLATION_NAME = "pointsTranslation";

    // COLLECTIONS QUE DEPENDEN DE COMPETITION
    static public String OPS_LOG_NAME = "opsLog".concat(STATE_ID);
    static public String TEMPLATE_CONTESTS_NAME = "templateContests".concat(STATE_ID);
    static public String TEMPLATE_MATCHEVENTS_NAME = "templateMatchEvents".concat(STATE_ID);
    static public String TEMPLATE_SOCCERTEAMS_NAME = "templateSoccerTeams".concat(STATE_ID);
    static public String TEMPLATE_SOCCERPLAYERS_NAME = "templateSoccerPlayers".concat(STATE_ID);
    static public String CANCELLED_CONTESTENTRIES_NAME = "cancelledContestEntries".concat(STATE_ID);
    static public String CONTESTS_NAME = "contests".concat(STATE_ID);
    static public String STATS_SIMULATION_NAME = "statsSimulation".concat(STATE_ID);
    static public String OPTA_COMPETITIONS_NAME = "optaCompetitions".concat(STATE_ID);
    static public String OPTA_EVENTS_NAME = "optaEvents".concat(STATE_ID);
    static public String OPTA_PLAYERS_NAME = "optaPlayers".concat(STATE_ID);
    static public String OPTA_TEAMS_NAME = "optaTeams".concat(STATE_ID);
    static public String OPTA_MATCHEVENTS_NAME = "optaMatchEvents".concat(STATE_ID);
    static public String OPTA_MATCHEVENT_STATS_NAME = "optaMatchEventStats".concat(STATE_ID);
    static public String JOBS_NAME = "jobs".concat(STATE_ID);
    static public String NOTIFICATIONS_NAME = "notifications".concat(STATE_ID);
    static public String OPTA_PROCESSOR_NAME = "optaProcessor".concat(STATE_ID);
    static public String SIMULATOR_NAME = "simulator".concat(STATE_ID);

    static public MongoCollection opsLog() { return _jongo.getCollection(OPS_LOG_NAME); }

    static public MongoCollection sessions() { return _jongo.getCollection(SESSIONS_NAME); }
    static public MongoCollection users() { return _jongo.getCollection(USERS_NAME); }

    static public MongoCollection templateContests() { return _jongo.getCollection(TEMPLATE_CONTESTS_NAME); }
    static public MongoCollection templateMatchEvents() { return _jongo.getCollection(TEMPLATE_MATCHEVENTS_NAME); }
    static public MongoCollection templateSoccerTeams() { return _jongo.getCollection(TEMPLATE_SOCCERTEAMS_NAME); }
    static public MongoCollection templateSoccerPlayers() { return _jongo.getCollection(TEMPLATE_SOCCERPLAYERS_NAME); }
    static public MongoCollection cancelledContestEntries() { return _jongo.getCollection(CANCELLED_CONTESTENTRIES_NAME); }

    static public MongoCollection contests() { return _jongo.getCollection(CONTESTS_NAME); }

    static public MongoCollection statsSimulation() { return _jongo.getCollection(STATS_SIMULATION_NAME); }

    static public MongoCollection optaCompetitions() { return _jongo.getCollection(OPTA_COMPETITIONS_NAME); }
    static public MongoCollection optaEvents() { return _jongo.getCollection(OPTA_EVENTS_NAME); }
    static public MongoCollection optaPlayers() { return _jongo.getCollection(OPTA_PLAYERS_NAME); }
    static public MongoCollection optaTeams() { return _jongo.getCollection(OPTA_TEAMS_NAME); }
    static public MongoCollection optaMatchEvents() { return _jongo.getCollection(OPTA_MATCHEVENTS_NAME); }
    static public MongoCollection optaMatchEventStats() { return _jongo.getCollection(OPTA_MATCHEVENT_STATS_NAME); }
    static public MongoCollection pointsTranslation() { return _jongo.getCollection(POINTS_TRANSLATION_NAME); }

    static public MongoCollection jobs() { return _jongo.getCollection(JOBS_NAME); }
    static public MongoCollection accountingTransactions() { return _jongo.getCollection(ACCOUNTING_TRANSACTIONS_NAME); }

    static public MongoCollection notifications() {return _jongo.getCollection(NOTIFICATIONS_NAME);}

    static public MongoCollection orders() { return _jongo.getCollection(ORDERS_NAME); }
    static public MongoCollection refunds() { return _jongo.getCollection(REFUNDS_NAME); }
    static public MongoCollection paypalResponses() { return _jongo.getCollection(PAYPAL_RESPONSES_NAME); }

    static public MongoCollection promos() { return _jongo.getCollection(PROMOS_NAME); }
    static public MongoCollection bonus() { return _jongo.getCollection(BONUS_NAME); }

    static public MongoCollection optaProcessor()  { return _jongo.getCollection(OPTA_PROCESSOR_NAME); }
    static public MongoCollection simulator() { return _jongo.getCollection(SIMULATOR_NAME); }


    static public TargetEnvironment getTargetEnvironment() {
        return _targetEnvironment;
    }

    static public boolean isLocalHostTargetEnvironment() {
        return TargetEnvironment.LOCALHOST == _targetEnvironment;
    }

    // Desde fuera se necesita acceder al gestor de actores para poder mandar mensajes desde el UI de administracion
    static public Actors actors() {
        return _actors;
    }

    static public void init(InstanceRole instanceRole, TargetEnvironment targetEnv, SystemMode systemMode) {

        if ((!Play.isDev() || instanceRole != InstanceRole.DEVELOPMENT_ROLE) && targetEnv != TargetEnvironment.LOCALHOST) {
            throw new RuntimeException("WTF 05 Intento de inicializar un entorno remoto sin ser una maquina de desarrollo");
        }

        _instanceRole = instanceRole;
        _targetEnvironment = targetEnv;

        initMongo(readMongoUriForEnvironment(_targetEnvironment));
        initPostgresDB();

        _actors = new Actors(_instanceRole, targetEnv, systemMode);

        Logger.debug("Akka: parallelism-factor: {} parallelism-min: {} parallelism-max: {}",
                Play.application().configuration().getString("play.akka.actor.default-dispatcher.fork-join-executor.parallelism-factor"),
                Play.application().configuration().getString("play.akka.actor.default-dispatcher.fork-join-executor.parallelism-min"),
                Play.application().configuration().getString("play.akka.actor.default-dispatcher.fork-join-executor.parallelism-max"));
    }

    static public void shutdown() {

        if (_actors != null) {
            _actors.shutdown();
            _actors = null;
        }

        if (_mongoClient != null) {
            _mongoClient.close();
            _mongoClient = null;
        }
    }

    static public void runCommand(String command, String javascript) {
        _jongo.runCommand(command, javascript).map(new RawResultHandler<DBObject>());
    }

    static private boolean initMongo(String mongodbUri) {
        boolean bSuccess = false;
        MongoClientURI mongoClientURI = new MongoClientURI(mongodbUri);

        Logger.info("The MongoDB is {}/{}", mongoClientURI.getHosts(), mongoClientURI.getDatabase());

        try {
            _mongoClient = new MongoClient(mongoClientURI);
            _mongoDB = _mongoClient.getDB(mongoClientURI.getDatabase());

            SimpleModule module = new SimpleModule();
            module.addDeserializer(Money.class, new JacksonJodaMoney.MoneyDeserializer());
            module.addSerializer(Money.class, new JacksonJodaMoney.MoneySerializer());

            Mapper mapper = new JacksonMapper.Builder()
                    .disable(MapperFeature.AUTO_DETECT_GETTERS)
                    .disable(MapperFeature.AUTO_DETECT_IS_GETTERS)
                    .disable(MapperFeature.AUTO_DETECT_SETTERS)
                    .registerModule(module)
                    .build();

            _jongo = new Jongo(_mongoDB, mapper);

            // Make sure our DB has the neccesary collections and indexes
            ensureMongoDB();

            // Realizar las migraciones que hagan falta
            Migrations.applyAll();

            bSuccess = true;
        }
        catch (Exception exc) {
            Logger.error("Error initializating MongoDB {}/{}: {}", mongoClientURI.getHosts(),
                                                                   mongoClientURI.getDatabase(), exc.toString());
        }

        return bSuccess;
    }

    private static void initPostgresDB() {

        Logger.info("Ejecutando migraciones Flyway.");

        Flyway flyway = new Flyway();
        flyway.setDataSource(play.db.DB.getDataSource());

        // Las localizaciones puede ser "filesystem:" o "classpath:". Cuando necesitemos alguna migracion en Java, mirar
        // bien el manual de Flyway.
        flyway.setLocations("filesystem:common/app/model/migrations");

        // Como venimos de una DB ya existente, marcamos la primera migracion como baseline
        flyway.setBaselineOnMigrate(true);
        flyway.migrate();

        Logger.info("Migraciones Flyway ejecutadas. Estas son todas:");

        for (MigrationInfo info : flyway.info().all()) {
            Logger.info("V{} - Script: {} - State: {}", info.getVersion(), info.getScript(), info.getState());
        }
    }

    static private String readMongoUriForEnvironment(TargetEnvironment env) {

        String ret = Play.application().configuration().getString("mongodb.uri");

        if (env != TargetEnvironment.LOCALHOST) {
            try {
                ret = ProcessExec.exec("heroku config:get MONGOHQ_URL -a " + env.herokuAppName);
            }
            catch (IOException e) {
                Logger.error("WTF 8266 Sin permisos, o sin heroku instalado. Falling back to local.");
            }
        }

        return ret;
    }

    static public void reset(boolean forSnapshot) {

        Logger.debug("Model.reset in progress...");

        if (!isLocalHostTargetEnvironment()) {
            throw new RuntimeException("WTF 9151 Intento de reset con TargetEnvironment remoto");
        }

        _actors.preReset();

        dropMongoDB(forSnapshot);

        if (!forSnapshot) {
            ensureMongoDB();

            PointsTranslation.createDefault();
            TemplateSoccerTeam.createInvalidTeam();

            MockData.ensureMockDataUsers();
            MockData.ensureCompetitions();
        }

        // Debido a que no tenemos una fecha global distribuida, tenemos que resetear manualmente. Obviamente se quedara
        // mal en todas las maquinas adonde no ha llegado este reset(), asi que los tests no funcionan con varios Web Process
        GlobalDate.setFakeDate(null);

        // Ya podemos volver a arrancar los actores
        _actors.postReset();

        Logger.debug("Model.reset done");
    }

    static private void dropMongoDB(boolean dropSystemUsers) {

        for (String collName : _mongoDB.getCollectionNames()) {
            if (collName.contains("system.")) {
                if (collName.equals("system.users") && dropSystemUsers) {
                    _mongoDB.getCollection(collName).drop();
                }
            }
            else {
                _mongoDB.getCollection(collName).drop();
            }

            Logger.debug("Collection {} dropped", collName);
        }

        Logger.debug("All collections dropped");
    }

    static private void ensureMongoDB() {
        ensureUsersDB(_mongoDB);
        ensureOptaDB(_mongoDB);
        ensureContestsDB(_mongoDB);
        ensureTransactionsDB(_mongoDB);
        ensureNotificationsDB(_mongoDB);
        ensurePromosDB(_mongoDB);
        ensureBonusDB(_mongoDB);

        MockData.ensureCompetitions();
    }

    static private void ensureUsersDB(DB theMongoDB) {
        DBCollection users = theMongoDB.getCollection(USERS_NAME);

        // Si creando nuevo indice sobre datos que ya existan => .append("dropDups", true)
        users.createIndex(new BasicDBObject("email", 1), new BasicDBObject("unique", true));
        users.createIndex(new BasicDBObject("nickName", 1), new BasicDBObject("unique", true));
        users.createIndex(new BasicDBObject("deviceUUID", 1));

        // Do we need the sessionToken to be unique? SecureRandom guarantees it to be unique, doesn't it?
        // http://www.kodyaz.com/images/pics/random-number-generator-dilbert-comic.jpg
        DBCollection sessions = theMongoDB.getCollection(SESSIONS_NAME);
        sessions.createIndex(new BasicDBObject("sessionToken", 1), new BasicDBObject("unique", true));
    }

    private static void ensureOptaDB(DB theMongoDB) {

        DBCollection optaCompetitions = theMongoDB.getCollection(OPTA_COMPETITIONS_NAME);
        optaCompetitions.createIndex(new BasicDBObject("competitionId", 1));

        DBCollection optaEvents = theMongoDB.getCollection(OPTA_EVENTS_NAME);
        optaEvents.createIndex(new BasicDBObject("eventId", 1));
        optaEvents.createIndex(new BasicDBObject("gameId", 1));
        optaEvents.createIndex(new BasicDBObject("optaPlayerId", 1));
        optaEvents.createIndex(new BasicDBObject("competitionId", 1));
        optaEvents.createIndex(new BasicDBObject("homeTeamId", 1));
        optaEvents.createIndex(new BasicDBObject("awayTeamId", 1));

        DBCollection optaPlayers = theMongoDB.getCollection(OPTA_PLAYERS_NAME);
        optaPlayers.createIndex(new BasicDBObject("optaPlayerId", 1));

        DBCollection optaTeams = theMongoDB.getCollection(OPTA_TEAMS_NAME);
        DBCollection optaMatchEvents = theMongoDB.getCollection("optaMatchEvents");

        DBCollection pointsTranslation = theMongoDB.getCollection(POINTS_TRANSLATION_NAME);
        pointsTranslation.createIndex(new BasicDBObject("eventTypeId", 1));

        DBCollection optaProcessor = theMongoDB.getCollection(OPTA_PROCESSOR_NAME);
        optaProcessor.createIndex(new BasicDBObject("stateId", 1));
    }

    static private void ensureContestsDB(DB theMongoDB) {

        DBCollection templateContests = theMongoDB.getCollection(TEMPLATE_CONTESTS_NAME);
        templateContests.createIndex(new BasicDBObject("templateMatchEventIds", 1));
        templateContests.createIndex(new BasicDBObject("state", 1));

        DBCollection templateMatchEvents = theMongoDB.getCollection(TEMPLATE_MATCHEVENTS_NAME);
        templateMatchEvents.createIndex(new BasicDBObject("optaMatchEventId", 1));
        templateMatchEvents.createIndex(new BasicDBObject("startDate", 1));

        DBCollection templateSoccerTeams = theMongoDB.getCollection(TEMPLATE_SOCCERTEAMS_NAME);
        templateSoccerTeams.createIndex(new BasicDBObject("optaTeamId", 1));

        DBCollection templateSoccerPlayers = theMongoDB.getCollection(TEMPLATE_SOCCERPLAYERS_NAME);
        templateSoccerPlayers.createIndex(new BasicDBObject("templateTeamId", 1));
        templateSoccerPlayers.createIndex(new BasicDBObject("optaPlayerId", 1));

        DBCollection contests = theMongoDB.getCollection(CONTESTS_NAME);
        contests.createIndex(new BasicDBObject("templateContestId", 1));
        contests.createIndex(new BasicDBObject("templateMatchEventIds", 1));
        contests.createIndex(new BasicDBObject("state", 1));
        contests.createIndex(new BasicDBObject("contestEntries._id", 1));
        contests.createIndex(new BasicDBObject("contestEntries.userId", 1));

        if (!theMongoDB.collectionExists(CANCELLED_CONTESTENTRIES_NAME)) {
            theMongoDB.createCollection(CANCELLED_CONTESTENTRIES_NAME, new BasicDBObject());
        }

        DBCollection statsSimulation = theMongoDB.getCollection(STATS_SIMULATION_NAME);
        statsSimulation.createIndex(new BasicDBObject("optaMatchEventId", 1));

        DBCollection jobs = theMongoDB.getCollection(JOBS_NAME);
        jobs.createIndex(new BasicDBObject("state", 1));
    }

    static private void ensureTransactionsDB(DB theMongoDB) {
        DBCollection accountingTransactions = theMongoDB.getCollection(ACCOUNTING_TRANSACTIONS_NAME);
        accountingTransactions.createIndex(new BasicDBObject("accountOps.accountId", 1).append("accountOps.seqId", 1), new BasicDBObject("unique", true));
        accountingTransactions.createIndex(new BasicDBObject("currencyCode", 1));
    }

    static private void ensureNotificationsDB(DB theMongoDB) {
        DBCollection notifications = theMongoDB.getCollection(NOTIFICATIONS_NAME);
        notifications.createIndex(new BasicDBObject("topic", 1));
        notifications.createIndex(new BasicDBObject("reason", 1));
        notifications.createIndex(new BasicDBObject("recipients", 1));
        notifications.createIndex(new BasicDBObject("readed", 1));
    }

    static private void ensurePromosDB(DB theMongoDB) {
        if (!theMongoDB.collectionExists(PROMOS_NAME)) {
            theMongoDB.createCollection(PROMOS_NAME, new BasicDBObject());
        }
    }

    static private void ensureBonusDB(DB theMongoDB) {
        if (!theMongoDB.collectionExists(BONUS_NAME)) {
            theMongoDB.createCollection(BONUS_NAME, new BasicDBObject());
        }
    }

    /**
     * Query de una lista de ObjectIds (en una misma query)
     *
     * @param collection: MongoCollection a la que hacer la query
     * @param fieldId:    Identificador del campo a buscar (p ej, 'templateContestId')
     * @param objectIds: Lista de ObjectId (de mongoDb)
     */
    public static Find findObjectIds(MongoCollection collection, String fieldId, List<ObjectId> objectIds) {
        return collection.find(String.format("{%s: {$in: #}}", fieldId), objectIds);
    }

    public static Find findFields(MongoCollection collection, String fieldId, List<String> values) {
        return collection.find(String.format("{\"%s\": {$in: #}}", fieldId), values);
    }

    static private TargetEnvironment _targetEnvironment;
    static private InstanceRole _instanceRole;

    // http://docs.mongodb.org/ecosystem/tutorial/getting-started-with-java-driver/
    static private MongoClient _mongoClient;

    // From http://docs.mongodb.org/ecosystem/drivers/java-concurrency/
    // DB and DBCollection are completely thread safe. In fact, they are cached so you get the same instance no matter what.
    static private DB _mongoDB;

    // Jongo is thread safe too: https://groups.google.com/forum/#!topic/jongo-user/KwukXi5Vm7c
    static private Jongo _jongo;

    // Mantenemos aqui nuestro unico Actors para asegurar que tiene el mismo ciclo de vida que nosotros
    static private Actors _actors;
}
