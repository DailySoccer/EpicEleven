package model;

import com.fasterxml.jackson.annotation.JsonView;
import com.mongodb.DuplicateKeyException;
import com.mongodb.MongoException;
import com.mongodb.WriteConcern;
import com.mongodb.WriteResult;
import model.jobs.CancelContestJob;
import model.jobs.Job;
import model.opta.OptaMatchEvent;
import org.bson.types.ObjectId;
import org.joda.money.Money;
import org.jongo.marshall.jackson.oid.Id;
import play.Logger;
import utils.ListUtils;
import utils.MoneyUtils;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

public class TemplateContest implements JongoId {
    public static final String FILL_WITH_MOCK_USERS = "%MockUsers";

    @Id
    public ObjectId templateContestId;

    @JsonView(JsonViews.Extended.class)
    public ContestState state = ContestState.OFF;

    public String name;

    @JsonView(JsonViews.NotForClient.class)
    public int minInstances;        // Minimum desired number of instances that we want running at any given moment

    @JsonView(JsonViews.NotForClient.class)
    public int maxInstances = 0;    // Máximo número de instancias que pueden existir (estén o no llenas)

    public int minEntries = 2;
    public int maxEntries;

    public int salaryCap;
    public Money entryFee;
    public PrizeType prizeType;
    public Money prizePool;
    public float prizeMultiplier = 1.0f;

    @JsonView(JsonViews.NotForClient.class)
    public Integer minManagerLevel;
    @JsonView(JsonViews.NotForClient.class)
    public Integer maxManagerLevel;

    public Integer minTrueSkill;
    public Integer maxTrueSkill;

    public Date startDate;

    public String optaCompetitionId;

    @JsonView(value = {JsonViews.Extended.class, JsonViews.CreateContest.class})
    public List<ObjectId> templateMatchEventIds;

    @JsonView(JsonViews.Extended.class)
    public List<InstanceSoccerPlayer> instanceSoccerPlayers;

    @JsonView(JsonViews.NotForClient.class)
    public Date activationAt;

    @JsonView(JsonViews.NotForClient.class)
    public Date createdAt;

    @JsonView(JsonViews.NotForClient.class)
    public String specialImage;

    public boolean simulation = false;

    @JsonView(JsonViews.NotForClient.class)
    public boolean customizable = false;

    public TemplateContest() { }

    public TemplateContest(String name, int minInstances, int maxInstances, int maxEntries, SalaryCap salaryCap,
                           Money entryFee, PrizeType prizeType, Date activationAt,
                           List<String> templateMatchEvents) {
        this(name, minInstances, maxInstances, maxEntries, salaryCap, entryFee, 1.0f, prizeType, activationAt, templateMatchEvents);
    }

    public TemplateContest(String name, int minInstances, int maxInstances, int maxEntries, SalaryCap salaryCap,
                            Money entryFee, float prizeMultiplier, PrizeType prizeType, Date activationAt,
                            List<String> templateMatchEvents) {

        this.name = name;
        this.minInstances = minInstances;
        this.maxInstances = maxInstances;
        this.minEntries = 2;
        this.maxEntries = maxEntries;
        this.salaryCap = salaryCap.money;
        this.entryFee = entryFee;
        this.prizeMultiplier = prizeMultiplier;
        this.prizeType = prizeType;
        this.activationAt = activationAt;

        Date startDate = null;
        this.templateMatchEventIds = new ArrayList<>();
        for (String templateMatchEventId : templateMatchEvents) {
            TemplateMatchEvent templateMatchEvent = TemplateMatchEvent.findOneFromOptaId(templateMatchEventId);
            this.optaCompetitionId = templateMatchEvent.optaCompetitionId;
            this.templateMatchEventIds.add(templateMatchEvent.templateMatchEventId);

            if (startDate == null || templateMatchEvent.startDate.before(startDate)) {
                startDate = templateMatchEvent.startDate;
            }
        }
        this.startDate = startDate;
    }

    public TemplateContest copy() {
        TemplateContest cloned = new TemplateContest();

        cloned.templateContestId = templateContestId;
        cloned.state = state;
        cloned.name = name;
        cloned.minInstances = minInstances;
        cloned.maxInstances = maxInstances;
        cloned.minEntries = minEntries;
        cloned.maxEntries = maxEntries;

        cloned.salaryCap = salaryCap;
        cloned.entryFee = entryFee;
        cloned.prizeMultiplier = prizeMultiplier;
        cloned.prizePool = prizePool;
        cloned.prizeType = prizeType;

        cloned.minManagerLevel = minManagerLevel;
        cloned.maxManagerLevel = maxManagerLevel;
        cloned.minTrueSkill = minTrueSkill;
        cloned.maxTrueSkill = maxTrueSkill;

        cloned.startDate = startDate;
        cloned.optaCompetitionId = optaCompetitionId;

        cloned.templateMatchEventIds = new ArrayList<>(templateMatchEventIds);

        if (instanceSoccerPlayers != null) {
            cloned.instanceSoccerPlayers = new ArrayList<>(instanceSoccerPlayers);
        }

        cloned.activationAt = activationAt;
        cloned.createdAt = createdAt;

        cloned.simulation = simulation;
        cloned.specialImage = specialImage;

        return cloned;
    }

    public ObjectId getId() {
        return templateContestId;
    }

    public List<TemplateMatchEvent> getTemplateMatchEvents() {
        return TemplateMatchEvent.findAll(templateMatchEventIds);
    }

    static public TemplateContest findOne(ObjectId templateContestId) {
        return Model.templateContests().findOne("{_id : #}", templateContestId).as(TemplateContest.class);
    }

    static public TemplateContest findOne(ObjectId templateContestId, String projection) {
        return Model.templateContests().findOne("{_id : #}", templateContestId).projection(projection).as(TemplateContest.class);
    }

    static public TemplateContest findOne(String templateContestId) {
        return ObjectId.isValid(templateContestId)
                ? findOne(new ObjectId(templateContestId))
                : null;
    }

    static public TemplateContest findOne(String templateContestId, String projection) {
        return ObjectId.isValid(templateContestId)
                ? findOne(new ObjectId(templateContestId), projection)
                : null;
    }

    static public List<TemplateContest> findAll() {
        return ListUtils.asList(Model.templateContests().find().as(TemplateContest.class));
    }

    static public List<TemplateContest> findAllDraft() {
        return ListUtils.asList(Model.templateContests().find("{state: \"DRAFT\"}").as(TemplateContest.class));
    }

    static public List<TemplateContest> findAllDraftSimulations() {
        return ListUtils.asList(Model.templateContests().find("{state: \"DRAFT\", simulation: true}").as(TemplateContest.class));
    }

    static public List<TemplateContest> findAllActive() {
        return ListUtils.asList(Model.templateContests().find("{state: \"ACTIVE\"}").as(TemplateContest.class));
    }

    static public boolean existsAnyInState(ContestState contestState) {
        return Model.templateContests().find("{state: #}", contestState).limit(1).projection("{_id : 1}").as(TemplateContest.class).hasNext();
    }

    static public List<TemplateContest> findAllActiveOrLive() {
        return ListUtils.asList(Model.templateContests().find("{$or: [{state: \"ACTIVE\"}, {state: \"LIVE\"}]}").as(TemplateContest.class));
    }

    static public long countAllActiveOrLive() {
        return Model.templateContests().count("{$or: [{state: \"ACTIVE\"}, {state: \"LIVE\"}]}");
    }

    static public List<TemplateContest> findAllActiveNotSimulations() {
        return ListUtils.asList(Model.templateContests().find("{state: \"ACTIVE\", simulation: {$ne: true}}").as(TemplateContest.class));
    }

    static public List<TemplateContest> findAllCustomizable() {
        // return ListUtils.asList(Model.templateContests().find("{state: \"ACTIVE\", simulation: {$ne: true}, customizable: true}").as(TemplateContest.class));
        return ListUtils.asList(Model.templateContests().find("{state: \"ACTIVE\", customizable: true}").as(TemplateContest.class));
    }

    static public List<TemplateContest> findAllFromContests(List<Contest> contests) {

        ArrayList<ObjectId> templateContestIds = new ArrayList<>(contests.size());

        for (Contest contest : contests) {
            templateContestIds.add(contest.templateContestId);
        }

        return ListUtils.asList(Model.findObjectIds(Model.templateContests(), "_id", templateContestIds).as(TemplateContest.class));
    }

    static public List<TemplateContest> findAllByActivationAt(Date activationAt) {
        return ListUtils.asList(Model.templateContests()
                                     .find("{state: \"OFF\", activationAt: {$lte: #}, startDate: {$gte: #}}", activationAt, GlobalDate.getCurrentDate())
                                     .as(TemplateContest.class));
    }

    static public long getNumInstancesNotFull(ObjectId templateContestId) {
        return Model.contests().count("{templateContestId : #, freeSlots: {$gt: 0}}", templateContestId);
    }

    static public boolean hasMaxInstances(ObjectId templateContestId) {
        // Comprobamos si el template tiene maxInstances > 0 (tiene un límite de instancias que puede crear)
        return Model.templateContests().findOne("{_id : #, maxInstances: {$gt: 0}}", templateContestId).as(TemplateContest.class) != null;
    }

    public TemplateContest insert() {
        Model.templateContests().withWriteConcern(WriteConcern.SAFE).update("{_id: #}", templateContestId).upsert().with(this);
        return this;
    }

    /**
     *  Eliminar un template contest y sus dependencias
     */
    public static boolean remove(TemplateContest templateContest) {
        Logger.info("remove TemplateContest({}): {}", templateContest.templateContestId, templateContest.name);

        // Eliminar el template contest
        try {
            WriteResult result = Model.templateContests().remove("{_id: #, $or: [{state: \"DRAFT\"}, {state: \"OFF\"}]}", templateContest.templateContestId);
            if (result.getN() == 0) {
                throw new RuntimeException(String.format("Template Contest: Error removing %s", templateContest.templateContestId.toString()));
            }
        }
        catch(MongoException e) {
            Logger.error("WTF 6799", e);
        }

        return true;
    }

    public static void maintainingMinimumNumberOfInstances(ObjectId templateContestId) {
        TemplateContest templateContest = findOne(templateContestId);

        // Cuantas instancias tenemos creadas que no estén llenas?
        long instancesNotFull = Contest.countActiveNotFullFromTemplateContest(templateContestId);
        // Cuantas instancias tenemos creadas?
        long instances = (templateContest.maxInstances <= 0) ? 0 : Contest.countActiveFromTemplateContest(templateContestId);

        long limitInstances = (templateContest.maxInstances <= 0) ? Long.MAX_VALUE : templateContest.maxInstances;
        for (long i=0; i < (templateContest.minInstances - instancesNotFull) && (instances + i) < limitInstances; i++) {
            templateContest.instantiateContest();
        }
    }

    public Contest instantiateContest() {
        Contest contest = new Contest(this);
        state = ContestState.ACTIVE;
        contest.insert();
        return contest;
    }

    public void instantiate() {

        Logger.info("TemplateContest.instantiate: {}: activationAt: {}", name, GlobalDate.formatDate(activationAt));

        if (simulation) {
            setupSimulation();
        }

        instanceSoccerPlayers = TemplateSoccerPlayer.instanceSoccerPlayersFromMatchEvents(getTemplateMatchEvents());

        // Cuantas instancias tenemos creadas?
        long instances = Model.contests().count("{templateContestId: #}", templateContestId);

        boolean mockDataUsers = name.contains(FILL_WITH_MOCK_USERS);
        for (long i=instances; i < minInstances; i++) {
            Contest contest = instantiateContest();
            if (mockDataUsers) {
                MockData.addContestEntries(contest, (contest.maxEntries > 0) ? contest.maxEntries - 1 : 50);
            }
        }

        // Cuando hemos acabado de instanciar nuestras dependencias, nos ponemos en activo
        if (simulation) {
            // Con la simulación también hay que actualizar la lista de partidos "virtuales"
            Model.templateContests().update("{_id: #, state: \"OFF\"}", templateContestId).with("{$set: {state: \"ACTIVE\", instanceSoccerPlayers:#, templateMatchEventIds:#}}",
                    instanceSoccerPlayers, templateMatchEventIds);
        }
        else {
            Model.templateContests().update("{_id: #, state: \"OFF\"}", templateContestId).with("{$set: {state: \"ACTIVE\", instanceSoccerPlayers:#}}", instanceSoccerPlayers);
        }

        // Ya estamos activos!
        state = ContestState.ACTIVE;
    }

    public void setupSimulation() {
        Logger.debug("TemplateContest.SetupSimulation: " + templateContestId.toString());
        templateMatchEventIds = TemplateMatchEvent.createSimulationsWithStartDate(templateMatchEventIds, startDate);
    }

    /**
     *  Estado del partido
     */
    public boolean isStarted() {
        // El TemplateContest ha comenzado si cualquiera de sus partidos ha comenzado
        TemplateMatchEvent matchEventStarted = Model.templateMatchEvents()
                .findOne("{_id: {$in: #}, gameStartedDate: {$exists: 1}}", templateMatchEventIds)
                .projection("{_id: 1}")
                .as(TemplateMatchEvent.class);
        return (matchEventStarted != null);
    }

    public static boolean isStarted(String templateContestId) {
        return findOne(new ObjectId(templateContestId)).isStarted();
    }

    public boolean isFinished() {
        // El TemplateContest ha terminado si todos sus partidos han terminado
        long numMatchEventsFinished = Model.templateMatchEvents()
                .count("{_id: {$in: #}, gameFinishedDate: {$exists: 1}}", templateMatchEventIds);
        return (numMatchEventsFinished == templateMatchEventIds.size());
    }

    public static boolean isFinished(String templateContestId) {
        return findOne(new ObjectId(templateContestId)).isFinished();
    }

    public boolean isSimulation()   { return simulation; }

    public static void publish(ObjectId templateContestId) {
        Model.templateContests().update("{_id: #, state: \"DRAFT\"}", templateContestId).with("{$set: {state: \"OFF\"}}");
    }

    public Money getPrizePool() {
        return (prizePool != null && prizePool.isPositive())
                ? prizePool
                : Prizes.getPool(simulation ? MoneyUtils.CURRENCY_MANAGER : MoneyUtils.CURRENCY_GOLD, entryFee, maxEntries, prizeMultiplier);
    }

    public InstanceSoccerPlayer getInstanceSoccerPlayer(ObjectId templateSoccerPlayerId) {
        InstanceSoccerPlayer instanceSoccerPlayer = null;
        for (InstanceSoccerPlayer soccerPlayer: instanceSoccerPlayers) {
            if (soccerPlayer.templateSoccerPlayerId.equals(templateSoccerPlayerId)) {
                instanceSoccerPlayer = soccerPlayer;
                break;
            }
        }
        if (instanceSoccerPlayer == null) {
            Logger.error ("WTF 7312: instanceSoccerPlayer == null -> {} ", templateSoccerPlayerId.toString());
        }
        return instanceSoccerPlayer;
    }

    public static void actionWhenMatchEventIsStarted(TemplateMatchEvent matchEvent) {
        // Los template contests (que incluyan este match event y que esten "activos") tienen que ser marcados como "live"
        Model.templateContests()
                .update("{templateMatchEventIds: {$in:[#]}, state: \"ACTIVE\"}", matchEvent.templateMatchEventId)
                .multi()
                .with("{$set: {state: \"LIVE\"}}");

        try {
            // Los Contests válidos pasarán a "live" O serán "cancelados"
            // Válido = Los que tengan un número válido de participantes
            List<Contest> contests = ListUtils.asList(Model.contests()
                    .find("{templateMatchEventIds: {$in:[#]}, state: \"ACTIVE\"}", matchEvent.templateMatchEventId).as(Contest.class));
            contests.forEach(contest -> {
                if (contest.getNumEntries() >= contest.minEntries) {
                    Model.contests()
                            .update("{_id: #, state: \"ACTIVE\"}", contest.contestId)
                            .multi()
                            .with("{$set: {state: \"LIVE\", startedAt: #}}", GlobalDate.getCurrentDate());
                }
                else {
                    // Cancelamos aquellos contests que no tengan el mínimo número de participantes
                    Job job = CancelContestJob.create(contest.contestId);
                    if (!job.isDone()) {
                        Logger.error("CancelContestJob {} error", contest.contestId);
                    }
                }
            });
        }
        catch(DuplicateKeyException e) {
            play.Logger.error("WTF 3333: actionWhenMatchEventIsStarted: {}", e.toString());
        }
    }

    public static void actionWhenMatchEventUpdated(TemplateMatchEvent matchEvent) {
        long startTime = System.currentTimeMillis();
        // Marcamos los torneos que se han actualizado con información de "live"
        Model.contests()
                .update("{state: \"LIVE\", templateMatchEventIds: {$in:[#]}}", matchEvent.templateMatchEventId)
                .multi()
                .with("{$set: {liveUpdatedAt: #}}", GlobalDate.getCurrentDate());
        Logger.debug("actionWhenMatchEventUpdated: {}", System.currentTimeMillis() - startTime);
    }

    public static void actionWhenMatchEventIsFinished(TemplateMatchEvent matchEvent) {
        // Buscamos los template contests que incluyan ese partido
        List<TemplateContest> templateContests = ListUtils.asList(Model.templateContests()
                .find("{templateMatchEventIds: {$in:[#]}}", matchEvent.templateMatchEventId).as(TemplateContest.class));

        if (!templateContests.isEmpty()) {
            templateContests.forEach( templateContest -> {
                // Si el contest ha terminado (true si todos sus partidos han terminado)
                if (templateContest.isFinished()) {
                    Model.templateContests().update("{_id: #, state: \"LIVE\"}", templateContest.templateContestId).with("{$set: {state: \"HISTORY\"}}");

                    Model.contests()
                            .update("{templateContestId: #, state: \"LIVE\"}", templateContest.templateContestId)
                            .multi()
                            .with("{$set: {state: \"HISTORY\", finishedAt: #}}", GlobalDate.getCurrentDate());
                }
            });
        }
        else {
            // Puede que sea un partido de los contests creados por un usuario
            if (matchEvent.isSimulation()) {
                List<Contest> contests = ListUtils.asList(Model.contests()
                        .find("{templateMatchEventIds: {$in:[#]}}", matchEvent.templateMatchEventId).as(Contest.class));

                contests.forEach( contest -> {
                    // Si el contest ha terminado (true si todos sus partidos han terminado)
                    if (contest.isFinished()) {
                        Model.contests()
                                .update("{_id: #, state: \"LIVE\"}", contest.contestId)
                                .multi()
                                .with("{$set: {state: \"HISTORY\", finishedAt: #}}", GlobalDate.getCurrentDate());
                    }
                });
            }
        }
    }
}
