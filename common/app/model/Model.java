package model;

import actors.Actors;
import com.fasterxml.jackson.databind.MapperFeature;
import com.mongodb.*;
import org.bson.types.ObjectId;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.jongo.Find;
import org.jongo.Jongo;
import org.jongo.Mapper;
import org.jongo.MongoCollection;
import org.jongo.marshall.jackson.JacksonMapper;
import play.Logger;
import play.Play;
import utils.InstanceRole;
import utils.ProcessExec;
import utils.TargetEnvironment;

import java.io.IOException;
import java.util.List;


public class Model {

    static public MongoCollection opsLog() { return _jongo.getCollection("opsLog"); }

    static public MongoCollection sessions() { return _jongo.getCollection("sessions"); }
    static public MongoCollection users() { return _jongo.getCollection("users"); }

    static public MongoCollection templateContests() { return _jongo.getCollection("templateContests"); }
    static public MongoCollection templateMatchEvents() { return _jongo.getCollection("templateMatchEvents"); }
    static public MongoCollection templateSoccerTeams() { return _jongo.getCollection("templateSoccerTeams"); }
    static public MongoCollection templateSoccerPlayers() { return _jongo.getCollection("templateSoccerPlayers"); }
    static public MongoCollection cancelledContestEntries() { return _jongo.getCollection("cancelledContestEntries"); }

    static public MongoCollection contests() { return _jongo.getCollection("contests"); }
    static public MongoCollection matchEvents() { return _jongo.getCollection("matchEvents"); }

    static public MongoCollection optaCompetitions() { return _jongo.getCollection("optaCompetitions"); }
    static public MongoCollection optaEvents() { return _jongo.getCollection("optaEvents"); }
    static public MongoCollection optaPlayers() { return _jongo.getCollection("optaPlayers"); }
    static public MongoCollection optaTeams() { return _jongo.getCollection("optaTeams"); }
    static public MongoCollection optaMatchEvents() { return _jongo.getCollection("optaMatchEvents"); }
    static public MongoCollection optaMatchEventStats() { return _jongo.getCollection("optaMatchEventStats"); }
    static public MongoCollection pointsTranslation() { return _jongo.getCollection("pointsTranslation"); }

    static public MongoCollection jobs() { return _jongo.getCollection("jobs"); }
    static public MongoCollection accountingTransactions() { return _jongo.getCollection("accountingTransactions"); }

    static public MongoCollection orders() { return _jongo.getCollection("orders"); }
    static public MongoCollection refunds() { return _jongo.getCollection("refunds"); }
    static public MongoCollection paypalResponses() { return _jongo.getCollection("paypalResponses"); }

    static public MongoCollection optaProcessor()  { return _jongo.getCollection("optaProcessor"); }
    static public MongoCollection simulator() { return _jongo.getCollection("simulator"); }


    static public TargetEnvironment getTargetEnvironment() {
        return _targetEnvironment;
    }

    static public void setTargetEnvironment(TargetEnvironment env) {

        if (env == _targetEnvironment) {
            Logger.error("WTF 5772 no me gusta que me repitan las cosas");
            return;
        }

        // Solo se puede cambiar el environment al que atacamos en maquinas de desarrollo, claro
        if (_instanceRole != InstanceRole.DEVELOPMENT_ROLE) {
            throw new RuntimeException("WTF 5771 are you nuts?");
        }

        // Cambiar el ataque del modelo a un environment distinto significa reinicializar mongo y rabbitmq
        if (initMongo(readMongoUriForEnvironment(env))) {
            _actors.setTargetEnvironment(env);

            // Ahora ya estamos en el environment solicitado
            _targetEnvironment = env;
        }
    }

    static public boolean isLocalHostTargetEnvironment() {
        return TargetEnvironment.LOCALHOST == _targetEnvironment;
    }

    // Desde fuera se necesita acceder al gestor de actores para poder mandar mensajes desde el UI de administracion
    static public Actors actors() {
        return _actors;
    }

    static public void init(InstanceRole instanceRole) {
        _instanceRole = instanceRole;
        _targetEnvironment = TargetEnvironment.LOCALHOST;   // En produccion no tiene significado puesto que no se puede cambiar

        initMongo(readMongoUriForEnvironment(_targetEnvironment));
        initPostgresDB();

        _actors = new Actors(_instanceRole);
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

    static private boolean initMongo(String mongodbUri) {
        boolean bSuccess = false;
        MongoClientURI mongoClientURI = new MongoClientURI(mongodbUri);

        Logger.info("The MongoDB is {}/{}", mongoClientURI.getHosts(), mongoClientURI.getDatabase());

        try {
            _mongoClient = new MongoClient(mongoClientURI);
            _mongoDB = _mongoClient.getDB(mongoClientURI.getDatabase());

            Mapper mapper = new JacksonMapper.Builder()
                    .disable(MapperFeature.AUTO_DETECT_GETTERS)
                    .disable(MapperFeature.AUTO_DETECT_IS_GETTERS)
                    .disable(MapperFeature.AUTO_DETECT_SETTERS)
                    .build();
            _jongo = new Jongo(_mongoDB, mapper);

            // Make sure our DB has the neccesary collections and indexes
            ensureMongoDB();

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
        _actors.restartActors();

        dropMongoDB(forSnapshot);

        if (!forSnapshot) {
            ensureMongoDB();

            PointsTranslation.createDefault();
            TemplateSoccerTeam.createInvalidTeam();

            MockData.ensureMockDataUsers();
            MockData.ensureCompetitions();
        }
    }

    static private void dropMongoDB(boolean dropSystemUsers) {

        for (String collName : _mongoDB.getCollectionNames()) {
            if (collName.startsWith("system.")) {
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
    }

    static private void ensureUsersDB(DB theMongoDB) {
        DBCollection users = theMongoDB.getCollection("users");

        // Si creando nuevo indice sobre datos que ya existan => .append("dropDups", true)
        users.createIndex(new BasicDBObject("email", 1), new BasicDBObject("unique", true));
        users.createIndex(new BasicDBObject("nickName", 1), new BasicDBObject("unique", true));

        // Do we need the sessionToken to be unique? SecureRandom guarantees it to be unique, doesn't it?
        // http://www.kodyaz.com/images/pics/random-number-generator-dilbert-comic.jpg
        DBCollection sessions = theMongoDB.getCollection("sessions");
        sessions.createIndex(new BasicDBObject("sessionToken", 1), new BasicDBObject("unique", true));
    }

    private static void ensureOptaDB(DB theMongoDB) {

        DBCollection optaCompetitions = theMongoDB.getCollection("optaCompetitions");
        optaCompetitions.createIndex(new BasicDBObject("competitionId", 1));

        DBCollection optaEvents = theMongoDB.getCollection("optaEvents");
        optaEvents.createIndex(new BasicDBObject("parentId", 1));
        optaEvents.createIndex(new BasicDBObject("eventId", 1));
        optaEvents.createIndex(new BasicDBObject("gameId", 1));
        optaEvents.createIndex(new BasicDBObject("optaPlayerId", 1));

        DBCollection optaPlayers = theMongoDB.getCollection("optaPlayers");
        optaPlayers.createIndex(new BasicDBObject("optaPlayerId", 1));

        DBCollection optaTeams = theMongoDB.getCollection("optaTeams");
        DBCollection optaMatchEvents = theMongoDB.getCollection("optaMatchEvents");

        DBCollection pointsTranslation = theMongoDB.getCollection("pointsTranslation");
        pointsTranslation.createIndex(new BasicDBObject("eventTypeId", 1));

        DBCollection optaProcessor = theMongoDB.getCollection("optaProcessor");
        optaProcessor.createIndex(new BasicDBObject("stateId", 1));
    }

    static private void ensureContestsDB(DB theMongoDB) {

        if (!theMongoDB.collectionExists("templateContests")) {
            DBCollection templateContests = theMongoDB.createCollection("templateContests", new BasicDBObject());
            templateContests.createIndex(new BasicDBObject("templateMatchEventIds", 1));
            templateContests.createIndex(new BasicDBObject("state", 1));
        }

        if (!theMongoDB.collectionExists("templateMatchEvents")) {
            DBCollection templateMatchEvents = theMongoDB.createCollection("templateMatchEvents", new BasicDBObject());
            templateMatchEvents.createIndex(new BasicDBObject("optaMatchEventId", 1));
        }

        if (!theMongoDB.collectionExists("templateSoccerTeams")) {
            DBCollection templateSoccerTeams = theMongoDB.createCollection("templateSoccerTeams", new BasicDBObject());
            templateSoccerTeams.createIndex(new BasicDBObject("optaTeamId", 1));
        }

        if (!theMongoDB.collectionExists("templateSoccerPlayers")) {
            DBCollection templateSoccerPlayers = theMongoDB.createCollection("templateSoccerPlayers", new BasicDBObject());
            templateSoccerPlayers.createIndex(new BasicDBObject("templateTeamId", 1));
            templateSoccerPlayers.createIndex(new BasicDBObject("optaPlayerId", 1));
        }

        if (!theMongoDB.collectionExists("contests")) {
            DBCollection contests = theMongoDB.createCollection("contests", new BasicDBObject());
            contests.createIndex(new BasicDBObject("templateContestId", 1));
            contests.createIndex(new BasicDBObject("templateMatchEventIds", 1));
            contests.createIndex(new BasicDBObject("state", 1));
            contests.createIndex(new BasicDBObject("contestEntries._id", 1));
            contests.createIndex(new BasicDBObject("contestEntries.userId", 1));
        }

        if (!theMongoDB.collectionExists("matchEvents")) {
            DBCollection matchEvents = theMongoDB.createCollection("matchEvents", new BasicDBObject());
            matchEvents.createIndex(new BasicDBObject("templateMatchEventId", 1));
            matchEvents.createIndex(new BasicDBObject("optaMatchEventId", 1));
        }

        if (!theMongoDB.collectionExists("cancelledContestEntries")) {
            theMongoDB.createCollection("cancelledContestEntries", new BasicDBObject());
        }
    }

    static private void ensureTransactionsDB(DB theMongoDB) {
        if (!theMongoDB.collectionExists("accountingTransactions")) {
            DBCollection accountingTransactions = theMongoDB.createCollection("accountingTransactions", new BasicDBObject());
             accountingTransactions.createIndex(new BasicDBObject("accountOps.accountId", 1).append("accountOps.seqId", 1), new BasicDBObject("unique", true));
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
