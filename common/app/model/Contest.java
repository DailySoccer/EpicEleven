package model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonView;
import com.mongodb.WriteConcern;
import model.accounting.AccountOp;
import model.accounting.AccountingTranPrize;
import org.apache.commons.lang3.StringUtils;
import org.bson.types.ObjectId;
import org.joda.money.CurrencyUnit;
import org.joda.money.Money;
import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormat;
import org.jongo.marshall.jackson.oid.Id;
import play.Logger;
import utils.ListUtils;
import utils.MoneyUtils;
import utils.TrueSkillHelper;
import utils.ViewProjection;

import java.util.*;
import java.util.stream.Collectors;

public class Contest implements JongoId {
    @JsonIgnore
    public static final String FILL_WITH_MOCK_USERS = "%MockUsers";

    @JsonIgnore
    public static final int MAX_PLAYERS_SAME_TEAM = 4;

    @Id
    public ObjectId contestId;
    public ObjectId templateContestId;

    @JsonView(JsonViews.NotForClient.class)
    public Date createdAt;

    @JsonView(JsonViews.NotForClient.class)
    public Date startedAt;

    @JsonView(JsonViews.NotForClient.class)
    public Date finishedAt;

    @JsonView(JsonViews.NotForClient.class)
    public Date canceledAt;

    @JsonView(JsonViews.NotForClient.class)
    public Date closedAt;

    @JsonView(value = {JsonViews.Public.class, JsonViews.AllContests.class})
    public ContestState state = ContestState.OFF;

    public String name;

    @JsonView(value = {JsonViews.Public.class, JsonViews.AllContests.class})
    public List<ContestEntry> contestEntries = new ArrayList<>();

    @JsonView(JsonViews.Public.class)
    public int getNumEntries() {
        return contestEntries.size();
    }
    private void setNumEntries(int blah) { }    // Para poder deserializar lo que nos llega por la red sin usar FAIL_ON_UNKNOWN_PROPERTIES

    public int minEntries = 2;
    public int maxEntries;

    @JsonView(JsonViews.NotForClient.class)
    public int freeSlots;

    public int salaryCap;

    @JsonView(value = {JsonViews.Public.class, JsonViews.AllContests.class})
    public Money entryFee;

    @JsonView(value = {JsonViews.Public.class, JsonViews.AllContests.class})
    public float prizeMultiplier = 0.9f;

    @JsonView(value = {JsonViews.Public.class, JsonViews.AllContests.class})
    public Money prizePool;

    @JsonView(value = {JsonViews.Public.class, JsonViews.AllContests.class})
    public PrizeType prizeType;

    public Integer minManagerLevel;
    public Integer maxManagerLevel;
    public Integer minTrueSkill;
    public Integer maxTrueSkill;

    public Date startDate;

    @JsonView(JsonViews.NotForClient.class)
    public Date activationAt;

    public String optaCompetitionId;

    @JsonView(value={JsonViews.ContestInfo.class, JsonViews.Extended.class, JsonViews.MyLiveContests.class})
    public List<ObjectId> templateMatchEventIds = new ArrayList<>();

    @JsonView(value={JsonViews.Extended.class, JsonViews.MyLiveContests.class, JsonViews.InstanceSoccerPlayers.class})
    public List<InstanceSoccerPlayer> instanceSoccerPlayers = new ArrayList<>();

    @JsonView(JsonViews.NotForClient.class)
    public List<Object> pendingJobs;

    @JsonView(JsonViews.NotForClient.class)
    public boolean closed = false;

    public boolean simulation = false;

    public ObjectId authorId;

    public String specialImage;

    public Contest() {}

    public Contest(Contest template) {
        setupFromContest(template);
    }

    public Contest(TemplateContest template) {
        setupFromTemplateContest(template);
    }

    public void setupFromContest(Contest template) {
        templateContestId = template.templateContestId;
        state = template.state;
        name = template.name;
        minEntries = template.minEntries;
        maxEntries = template.maxEntries;
        freeSlots = template.maxEntries;
        salaryCap = template.salaryCap;
        entryFee = template.entryFee;
        prizeMultiplier = template.prizeMultiplier;
        prizePool = template.prizePool;
        prizeType = template.prizeType;
        minManagerLevel = template.minManagerLevel;
        maxManagerLevel = template.maxManagerLevel;
        minTrueSkill = template.minTrueSkill;
        maxTrueSkill = template.maxTrueSkill;
        startDate = template.startDate;
        activationAt = template.activationAt;
        optaCompetitionId = template.optaCompetitionId;
        templateMatchEventIds = template.templateMatchEventIds;
        instanceSoccerPlayers = template.instanceSoccerPlayers;
        simulation = template.simulation;
        specialImage = template.specialImage;
        createdAt = GlobalDate.getCurrentDate();
    }

    public void setupFromTemplateContest(TemplateContest template) {
        templateContestId = template.templateContestId;
        state = template.state;
        name = template.name;
        minEntries = template.minEntries;
        maxEntries = template.maxEntries;
        freeSlots = template.maxEntries;
        salaryCap = template.salaryCap;
        entryFee = template.entryFee;
        prizeMultiplier = template.prizeMultiplier;
        prizePool = template.prizePool != null && template.prizePool.isPositive() ? template.prizePool : null;
        prizeType = template.prizeType;
        minManagerLevel = template.minManagerLevel;
        maxManagerLevel = template.maxManagerLevel;
        minTrueSkill = template.minTrueSkill;
        maxTrueSkill = template.maxTrueSkill;
        startDate = template.startDate;
        activationAt = template.activationAt;
        optaCompetitionId = template.optaCompetitionId;
        templateMatchEventIds = template.templateMatchEventIds;
        instanceSoccerPlayers = template.instanceSoccerPlayers;
        simulation = template.simulation;
        specialImage = template.specialImage;
        createdAt = GlobalDate.getCurrentDate();
    }

    public Contest duplicate() {
        Contest contest = new Contest(this);
        contest.state = ContestState.ACTIVE;
        contest.insert();
        return contest;
    }

    public ObjectId getId() { return contestId; }

    public boolean isFull() { return maxEntries > 0 && getNumEntries() >= maxEntries; }


    public List<TemplateMatchEvent> getTemplateMatchEvents() {
        return TemplateMatchEvent.findAll(templateMatchEventIds);
    }

    public int getMaxPlayersFromSameTeam() {
        return MAX_PLAYERS_SAME_TEAM;
    }

    public void insert() {
        Model.contests().withWriteConcern(WriteConcern.SAFE).insert(this);
    }

    public void instantiate() {

        Logger.info("Contest.instantiate: {}: activationAt: {}", name, GlobalDate.formatDate(activationAt));

        if (simulation) {
            setupSimulation();
        }

        instanceSoccerPlayers = TemplateSoccerPlayer.instanceSoccerPlayersFromMatchEvents(getTemplateMatchEvents());

        // Cuando hemos acabado de instanciar nuestras dependencias, nos ponemos en activo
        if (simulation) {
            // Con la simulación también hay que actualizar la lista de partidos "virtuales"
            Model.contests().update("{_id: #, state: \"OFF\"}", contestId).with("{$set: {state: \"ACTIVE\", instanceSoccerPlayers:#, templateMatchEventIds:#}}",
                    instanceSoccerPlayers, templateMatchEventIds);
        }
        else {
            Model.contests().update("{_id: #, state: \"OFF\"}", contestId).with("{$set: {state: \"ACTIVE\", instanceSoccerPlayers:#}}", instanceSoccerPlayers);
        }

        if (name.contains(FILL_WITH_MOCK_USERS)) {
            MockData.addContestEntries(this, (maxEntries > 0) ? maxEntries - 1 : 50);
        }

        // Ya estamos activos!
        state = ContestState.ACTIVE;
    }

    public ContestEntry findContestEntry(ObjectId contestEntryId) {
        ContestEntry ret = null;
        for (ContestEntry contestEntry : contestEntries) {
            if (contestEntry.contestEntryId.equals(contestEntryId)) {
                ret = contestEntry;
                break;
            }
        }
        return ret;
    }

    public boolean isCreatedByUser() {
        return authorId != null;
    }

    public boolean hasManagerLevelConditions() {
        return (minManagerLevel != null && maxManagerLevel != null) && (minManagerLevel != 0 || maxManagerLevel != User.MANAGER_POINTS.length);
    }

    public boolean managerLevelValid(int managerLevel) {
        return (managerLevel >= minManagerLevel && managerLevel <= maxManagerLevel);
    }

    public boolean hasTrueSkillConditions() {
        return (minTrueSkill != null && minTrueSkill != -1) || (maxTrueSkill != null && maxTrueSkill != -1);
    }

    public boolean trueSkillValid(int trueSkill) {
        return !hasTrueSkillConditions() || (trueSkill >= minTrueSkill && trueSkill <= maxTrueSkill);
    }

    static public Contest findOne(ObjectId contestId) {
        return Model.contests().findOne("{_id : #}", contestId).as(Contest.class);
    }

    static public Contest findOne(String contestId) {
        Contest aContest = null;
        if (ObjectId.isValid(contestId)) {
            aContest = Model.contests().findOne(new ObjectId(contestId)).as(Contest.class);
        }
        return aContest;
    }

    static public Contest findOne(ObjectId contestId, Class<?> projectionClass) {
        return Model.contests().findOne("{_id : #}", contestId).projection(ViewProjection.get(projectionClass, Contest.class)).as(Contest.class);
    }

    static public Contest findOneFromContestEntry(ObjectId contestEntryId) {
        return Model.contests().findOne("{'contestEntries._id' : #}", contestEntryId).as(Contest.class);
    }

    static public Contest findOneWaitingAuthor(ObjectId authorId) {
        return Model.contests().findOne("{state: \"WAITING_AUTHOR\", authorId: #}", authorId).as(Contest.class);
    }

    public static List<Contest> findAllFromUser(ObjectId userId) {
        return ListUtils.asList(Model.contests().find("{'contestEntries.userId': #}", userId).as(Contest.class));
    }

    static public List<Contest> findAllFromTemplateContest(ObjectId templateContestId) {
        return ListUtils.asList(Model.contests().find("{templateContestId: #}", templateContestId).as(Contest.class));
    }

    static public List<Contest> findAllByActivationAt(Date activationAt) {
        return ListUtils.asList(Model.contests()
                .find("{state: \"OFF\", activationAt: {$lte: #}, startDate: {$gte: #}}", activationAt, GlobalDate.getCurrentDate())
                .as(Contest.class));
    }

    static public List<Contest> findAllStartingIn(int hours) {
        DateTime oneHourInFuture = new DateTime(GlobalDate.getCurrentDate()).plusHours(hours);
        return ListUtils.asList(Model.contests()
                .find("{startDate: {$gt: #, $lte: # }}", GlobalDate.getCurrentDate(), oneHourInFuture.toDate())
                .as(Contest.class));
    }

    static public List<Contest> findAllFromTemplateContests(List<TemplateContest> templateContests) {
        return ListUtils.asList(Model.findObjectIds(Model.contests(), "templateContestId", ListUtils.convertToIdList(templateContests)).as(Contest.class));
    }

    static public List<Contest> findAllActive(Class<?> projectionClass) {
        return ListUtils.asList(Model.contests()
                .find("{state: \"ACTIVE\"}")
                .projection(ViewProjection.get(projectionClass, Contest.class))
                .as(Contest.class));
    }

    static public long countActiveNotFullFromTemplateContest(ObjectId templateContestId) {
        return Model.contests()
                .count("{templateContestId: #, state: \"ACTIVE\", freeSlots: {$gt: 0}}", templateContestId);
    }

    static public long countPlayedSimulations(ObjectId userId) {
        return Model.contests()
                .count("{'contestEntries.userId': #, state: \"HISTORY\", simulation: true}", userId);
    }

    static public long countWonSimulations(ObjectId userId) {
        return Model.contests()
                .count("{contestEntries: { $elemMatch: { userId: #, position: 0 } }, state: \"HISTORY\", simulation: true }", userId);
    }

    static public long countPlayedOfficial(ObjectId userId) {
        return Model.contests()
                .count("{'contestEntries.userId': #, state: \"HISTORY\", simulation: {$ne: true}}", userId);
    }

    static public long countWonOfficial(ObjectId userId) {
        return Model.contests()
                .count("{contestEntries: { $elemMatch: { userId: #, position: 0 } }, state: \"HISTORY\", simulation: {$ne: true}}", userId);
    }

    static public List<Contest> findAllActiveFromTemplateMatchEvent(ObjectId templateMatchEventId) {
        return ListUtils.asList(Model.contests()
                .find("{state: \"ACTIVE\", templateMatchEventIds: {$in:[#]}}", templateMatchEventId)
                .projection(ViewProjection.get(JsonViews.Public.class, Contest.class))
                .as(Contest.class));
    }

    static public List<Contest> findAllActiveNotFull(Class<?> projectionClass) {
        return ListUtils.asList(Model.contests()
                .find("{state: \"ACTIVE\", freeSlots: {$gt: 0}}")
                .projection(ViewProjection.get(projectionClass, Contest.class))
                .as(Contest.class));
    }

    static public List<Contest> findAllHistoryClosed() {
        return ListUtils.asList(Model.contests().find("{state: \"HISTORY\", closed: true}").as(Contest.class));
    }

    static public List<Contest> findAllHistoryNotClosed() {
        return ListUtils.asList(Model.contests().find("{state: \"HISTORY\", $or: [{closed: {$exists: false}}, {closed: false}]}").as(Contest.class));
    }

    static public List<Contest> findAllCanceled() {
        return ListUtils.asList(Model.contests().find("{state: \"CANCELED\"}").as(Contest.class));
    }

    static public List<Contest> findAllMyActive(ObjectId userId, Class<?> projectionClass) {
        return findAllMyContests(userId, "{state: \"ACTIVE\", \"contestEntries.userId\": #}", projectionClass);
    }

    static public List<Contest> findAllMyLive(ObjectId userId, Class<?> projectionClass) {
        return findAllMyContests(userId, "{state: \"LIVE\", \"contestEntries.userId\": #}", projectionClass);
    }

    static public List<Contest> findAllMyHistory(ObjectId userId, Class<?> projectionClass) {
        return findAllMyContests(userId, "{state: \"HISTORY\", \"contestEntries.userId\": #}", projectionClass);
    }

    static public List<Contest> findAllClosedAfter(Date closedAt) {
        return ListUtils.asList(Model.contests().find("{closed: true, closedAt: {$gt: #}}", closedAt).as(Contest.class));
    }

    static public long countByState(ObjectId userId, ContestState contestState) {
        return Model.contests().count("{state: #, \"contestEntries.userId\": #}", contestState, userId);
    }

    static public long countByStateAndSimulation(ObjectId userId, ContestState contestState, boolean simulation) {
        return Model.contests().count("{state: #, \"contestEntries.userId\": #, simulation: #}", contestState, userId, simulation);
    }

    static private List<Contest> findAllMyContests(ObjectId userId, String query, Class<?> projectionClass) {
        return ListUtils.asList(Model.contests()
                .find(query, userId)
                .projection(ViewProjection.get(projectionClass, Contest.class))
                .as(Contest.class));
    }

    public ContestEntry getContestEntryWithUser(ObjectId userId) {
        ContestEntry ret = null;
        for (ContestEntry contestEntry: contestEntries) {
            if (contestEntry.userId.equals(userId)) {
                ret = contestEntry;
                break;
            }
        }
        return ret;
    }

    public boolean containsContestEntryWithUser(ObjectId userId) {
        boolean contains = false;
        for (ContestEntry contestEntry: contestEntries) {
            if (contestEntry.userId.equals(userId)) {
                contains = true;
                break;
            }
        }
        return contains;
    }

    public void setupSimulation() {
        Logger.debug("Contest.SetupSimulation: " + contestId.toString());
        templateMatchEventIds = TemplateMatchEvent.createSimulationsWithStartDate(templateMatchEventIds, startDate);

        Model.contests().update("{_id: #, state: \"ACTIVE\"}", contestId).with("{$set: {templateMatchEventIds:#}}", templateMatchEventIds);
    }

    public boolean isFinished() {
        // El Contest ha terminado si todos sus partidos han terminado
        long numMatchEventsFinished = Model.templateMatchEvents()
                .count("{_id: {$in: #}, gameFinishedDate: {$exists: 1}}", templateMatchEventIds);
        return (numMatchEventsFinished == templateMatchEventIds.size());
    }

    public void closeContest() {

        if (!contestEntries.isEmpty()) {
            Prizes prizes = Prizes.findOne(prizeType, getNumEntries(), getPrizePool());

            updateRanking(prizes);
            givePrizes(prizes);
            updateWinner();

            Achievement.playedContest(this);
        }

        setClosed();
    }

    public User getWinner() {
        ContestEntry winner = getContestEntryInPosition(0);

        if (winner == null) {
            throw new RuntimeException("WTF 7221 winner == null");
        }

        return User.findOne(winner.userId);
    }

    public ContestEntry getContestEntryInPosition(int position) {
        ContestEntry result = null;

        for (ContestEntry contestEntry : contestEntries) {
            if (contestEntry.position == position) {
                result = contestEntry;
                break;
            }
        }

        return result;
    }

    private void updateRanking(Prizes prizes) {

        List<TemplateMatchEvent> templateMatchEvents = getTemplateMatchEvents();

        // Verificación...
        boolean matchEventsOK = (templateMatchEvents.stream().filter(templateMatchEvent -> templateMatchEvent.liveFantasyPoints != null).count() == templateMatchEvents.size());
        if (!matchEventsOK) {
            Logger.error("Contest: {}: TemplateMatchEvents sin LiveFantasyPoints", contestId.toString());
        }

        // Actualizamos los fantasy points
        for (ContestEntry contestEntry : contestEntries) {
            contestEntry.fantasyPoints = contestEntry.getFantasyPointsFromMatchEvents(templateMatchEvents);
        }

        // Los ordenamos según los fantasy points
        Collections.sort(contestEntries, new ContestEntryComparable());

        // Actualizamos sus "posiciones" y premio
        int index = 0;

        for (ContestEntry contestEntry : contestEntries) {
            contestEntry.position = index++;
            contestEntry.prize = prizes.getValue(contestEntry.position);
            contestEntry.updateRanking();
        }

        // Si es un torneo REAL, actualizaremos el TrueSkill de los participantes
        if (!simulation) {
            // Los contestEntries están ordenadas según sus posiciones
            updateTrueSkill();
        }
    }

    private void updateTrueSkill() {
        Logger.debug("TrueSkill: Contest: {} ({})", name, contestId.toString());

        // Calculamos el trueSkill de los participantes y actualizamos su información en la BDD
        Map<ObjectId, User> usersRating = TrueSkillHelper.RecomputeRatings(contestEntries);
        for (Map.Entry<ObjectId, User> entry : usersRating.entrySet()) {
            User user = entry.getValue();
            user.updateTrueSkillByContest(contestId);

            Achievement.trueSkillChanged(user, this);
        }
    }

    public String translatedName() {
        // DateTimeService.formatDateWithDayOfTheMonth(startDate))
        return StringUtils.capitalize(name.replaceAll("%StartDate", DateTimeFormat.forPattern("EEE, d MMM").withLocale(new Locale("es", "ES")).print(new DateTime(startDate)))
                .replaceAll("%MaxEntries", Integer.toString(maxEntries))
                .replaceAll("%SalaryCap", Integer.toString(new Double(salaryCap / 1000).intValue()))
                .replaceAll("%PrizeType", prizeType.name())
                .replaceAll("%EntryFee", entryFee.toString())
                .replaceAll("%MockUsers", ""));
    }

    private void givePrizes(Prizes prizes) {
        // Si el contest tiene premios para repartir...
        if (!prizeType.equals(PrizeType.FREE)) {

            // Si el premio es de Manager Points, lo gestionamos de forma diferente al GOLD
            if (simulation) {
                for (ContestEntry contestEntry : contestEntries) {
                    Money prize = prizes.getValue(contestEntry.position);
                    boolean amountPositive = (prize.getAmount().floatValue() > 0);

                    // Si el premio es de Manager Points, lo gestionamos de forma diferente al GOLD
                    if (prize.getCurrencyUnit().equals(MoneyUtils.CURRENCY_MANAGER)) {
                        User user = User.findOne(contestEntry.userId);

                        // En los torneos de simulación, se puede mejorar el nivel de manager
                        if (simulation && amountPositive) {
                            // Cuando reciba el premio, subirá su nivel de manager?
                            Money managerBalance = user.managerBalance;
                            int managerLevel = (int) User.managerLevelFromPoints(managerBalance);
                            int managerLevelUpdated = (int) User.managerLevelFromPoints(managerBalance.plus(prize));
                            if (managerLevelUpdated > managerLevel) {
                                UserNotification.managerLevelUp(managerLevelUpdated).sendTo(contestEntry.userId);
                            }
                        }

                        // En los torneos de Simulación hemos de actualizar los ManagerPoints de todos los participantes
                        user.addManagerBalance(prize);
                    }
                }
            }
            else {
                List<AccountOp> accounts = new ArrayList<>();
                for (ContestEntry contestEntry : contestEntries) {
                    Money prize = prizes.getValue(contestEntry.position);
                    boolean amountPositive = (prize.getAmount().floatValue() > 0);

                    // En los torneos Oficiales únicamente queremos incluir a los usuarios que ganen dinero
                    if (amountPositive) {
                        accounts.add(new AccountOp(contestEntry.userId, prize, User.getSeqId(contestEntry.userId) + 1));
                    }
                }

                // Alguién tendría que recibir un premio
                if (!accounts.isEmpty()) {
                    CurrencyUnit prizeCurrency = simulation ? MoneyUtils.CURRENCY_MANAGER : MoneyUtils.CURRENCY_GOLD;
                    AccountingTranPrize.create(prizeCurrency.getCode(), contestId, accounts);

                    // Si se jugaba con GOLD, se actualizarán las estadísticas de los ganadores
                    if (getPrizePool().getCurrencyUnit().equals(MoneyUtils.CURRENCY_GOLD)) {
                        for (AccountOp op : accounts) {
                            User winner = User.findOne(op.accountId);
                            winner.updateStats();
                        }
                    }
                }
                else {
                    Logger.error("WTF 2309: ContestId: {}", contestId.toString());
                }
            }
        }
    }

    private void updateWinner() {
        // Actualizamos las estadísticas de torneos ganados
        getWinner().updateStats();
    }

    private void setClosed() {
        Model.contests()
                .update("{_id: #, state: \"HISTORY\"}", contestId)
                .with("{$set: {closed: true, closedAt: #}}", GlobalDate.getCurrentDate());
    }

    public InstanceSoccerPlayer getInstanceSoccerPlayer(ObjectId templateSoccerPlayerId) {
        InstanceSoccerPlayer instanceSoccerPlayer = null;
        for (InstanceSoccerPlayer soccerPlayer: instanceSoccerPlayers) {
            if (soccerPlayer.templateSoccerPlayerId.equals(templateSoccerPlayerId)) {
                instanceSoccerPlayer = soccerPlayer;
                break;
            }
        }
        if (instanceSoccerPlayer == null)
            throw new RuntimeException("WTF 7312: instanceSoccerPlayer == null");
        return instanceSoccerPlayer;
    }

    public List<InstanceSoccerPlayer> getInstanceSoccerPlayers(List<ObjectId> ids) {
        List<InstanceSoccerPlayer> soccerPlayers = new ArrayList<>();
        for (ObjectId soccerPlayerId : ids) {
            for (InstanceSoccerPlayer instancePlayer : instanceSoccerPlayers) {
                if (soccerPlayerId.equals(instancePlayer.templateSoccerPlayerId)) {
                    soccerPlayers.add(instancePlayer);
                    break;
                }
            }
        }
        return soccerPlayers;
    }

    public List<InstanceSoccerPlayer> getInstanceSoccerPlayersWithFieldPos(FieldPos fieldPos, ContestEntry contestEntry) {
        List<InstanceSoccerPlayer> instanceSoccerPlayers = getInstanceSoccerPlayers(contestEntry.soccerIds);
        return instanceSoccerPlayers.stream().filter(instanceSoccerPlayer -> instanceSoccerPlayer.fieldPos.equals(fieldPos)).collect(Collectors.toList());
    }


    public Contest getSameContestWithFreeSlot(ObjectId userId) {
        String query = String.format("{templateContestId: #, 'contestEntries.%s': {$exists: false}, 'contestEntries.userId': {$ne:#}}", maxEntries-1);
        return Model.contests().findOne(query, templateContestId, userId).as(Contest.class);
    }

    public Money getPrizePool() {
        return (prizePool != null && prizePool.isPositive())
                ? prizePool
                : Prizes.getPool(simulation ? MoneyUtils.CURRENCY_MANAGER : MoneyUtils.CURRENCY_GOLD, entryFee, getNumEntries(), prizeMultiplier);
    }

    public Money getPrizePoolMin() {
        return Prizes.getPool(simulation ? MoneyUtils.CURRENCY_MANAGER : MoneyUtils.CURRENCY_GOLD, entryFee, minEntries, prizeMultiplier);
    }

    class ContestEntryComparable implements Comparator<ContestEntry>{
        @Override
        public int compare(ContestEntry o1, ContestEntry o2) {
            return (o2.fantasyPoints - o1.fantasyPoints);
        }
    }
}
