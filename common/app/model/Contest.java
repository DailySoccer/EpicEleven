package model;

import com.fasterxml.jackson.annotation.JsonView;
import model.accounting.AccountOp;
import model.accounting.AccountingTranPrize;
import org.bson.types.ObjectId;
import org.jongo.marshall.jackson.oid.Id;
import java.math.BigDecimal;

import utils.ListUtils;
import utils.ViewProjection;

import java.util.*;

public class Contest implements JongoId {
    @Id
    public ObjectId contestId;
    public ObjectId templateContestId;

    @JsonView(JsonViews.NotForClient.class)
    public Date createdAt;

    @JsonView(value = {JsonViews.Public.class, JsonViews.AllContests.class})
    public ContestState state = ContestState.OFF;

    public String name;

    @JsonView(value = {JsonViews.Public.class, JsonViews.AllContests.class})
    public List<ContestEntry> contestEntries = new ArrayList<>();

    @JsonView(JsonViews.Public.class)
    public int getNumEntries() {
        return contestEntries.size();
    }

    public int maxEntries;

    public int salaryCap;

    @JsonView(value = {JsonViews.Public.class, JsonViews.AllContests.class})
    public int entryFee;
    @JsonView(value = {JsonViews.Public.class, JsonViews.AllContests.class})
    public PrizeType prizeType;

    @JsonView(value={JsonViews.ContestInfo.class, JsonViews.Extended.class})
    public List<Integer> prizes;

    public Date startDate;

    public String optaCompetitionId;

    @JsonView(value={JsonViews.ContestInfo.class, JsonViews.Extended.class, JsonViews.MyLiveContests.class})
    public List<ObjectId> templateMatchEventIds = new ArrayList<>();

    @JsonView(value={JsonViews.Extended.class, JsonViews.MyLiveContests.class, JsonViews.InstanceSoccerPlayers.class})
    public List<InstanceSoccerPlayer> instanceSoccerPlayers = new ArrayList<>();

    @JsonView(JsonViews.NotForClient.class)
    public List<Object> pendingJobs;

    @JsonView(JsonViews.NotForClient.class)
    public boolean closed = false;

    public Contest() {}

    public Contest(TemplateContest template) {
        templateContestId = template.templateContestId;
        state = template.state;
        name = template.name;
        maxEntries = template.maxEntries;
        salaryCap = template.salaryCap;
        entryFee = template.entryFee;
        prizeType = template.prizeType;
        prizes = template.prizes;
        startDate = template.startDate;
        optaCompetitionId = template.optaCompetitionId;
        templateMatchEventIds = template.templateMatchEventIds;
        instanceSoccerPlayers = template.instanceSoccerPlayers;
        createdAt = GlobalDate.getCurrentDate();
    }

    public ObjectId getId() { return contestId; }

    public boolean isOff()      { return (state == ContestState.OFF); }
    public boolean isActive()   { return (state == ContestState.ACTIVE); }
    public boolean isLive()     { return (state == ContestState.LIVE); }
    public boolean isHistory()  { return (state == ContestState.HISTORY); }
    public boolean isCanceled() { return (state == ContestState.CANCELED); }

    public boolean isFull() { return getNumEntries() >= maxEntries; }

    public void setClosed() {
        Model.contests()
                .update("{_id: #, state: \"HISTORY\"}", contestId)
                .with("{$set: {closed: true}}");
    }

    public List<TemplateMatchEvent> getTemplateMatchEvents() {
        return TemplateMatchEvent.findAll(templateMatchEventIds);
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

    public static List<Contest> findAllFromUser(ObjectId userId) {
        return ListUtils.asList(Model.contests().find("{'contestEntries.userId': #}", userId).as(Contest.class));
    }

    static public List<Contest> findAllFromTemplateContest(ObjectId templateContestId) {
        return ListUtils.asList(Model.contests().find("{templateContestId: #}", templateContestId).as(Contest.class));
    }

    static public List<Contest> findAllNotCanceledFromTemplateContest(ObjectId templateContestId) {
        return ListUtils.asList(Model.contests().find("{templateContestId: #, state: { $ne: \"CANCELED\" }}", templateContestId).as(Contest.class));
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

    static public List<Contest> findAllActiveNotFull(Class<?> projectionClass) {
        return ListUtils.asList(Model.contests()
                .find("{$and: [{state: \"ACTIVE\"}, {$where: \"this.contestEntries.length < this.maxEntries\"}]}")
                .projection(ViewProjection.get(projectionClass, Contest.class))
                .as(Contest.class));
    }

    static public List<Contest> findAllActiveNotFullWithEntryFee(ObjectId templateMatchEventId) {
        return ListUtils.asList(Model.contests()
                .find("{$and: [{state: \"ACTIVE\", templateMatchEventIds: {$in:[#]}, entryFee: {$gt: 0}}, {$where: \"this.contestEntries.length < this.maxEntries\"}]}", templateMatchEventId)
                .projection(ViewProjection.get(JsonViews.Public.class, Contest.class))
                .as(Contest.class));
    }

    static public List<Contest> findAllHistoryNotClosed() {
        return ListUtils.asList(Model.contests().find("{state: \"HISTORY\", closed: false}").as(Contest.class));
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

    private void updateRanking() {
        if (contestEntries.isEmpty()) {
            return;
        }

        List<TemplateMatchEvent> templateMatchEvents = getTemplateMatchEvents();

        // Actualizamos los fantasy points
        for (ContestEntry contestEntry : contestEntries) {
            contestEntry.fantasyPoints = contestEntry.getFantasyPointsFromMatchEvents(templateMatchEvents);
        }
        // Los ordenamos según los fantasy points
        Collections.sort (contestEntries, new ContestEntryComparable());
        // Actualizamos sus "posiciones"
        int index = 0;
        for (ContestEntry contestEntry : contestEntries) {
            contestEntry.position = index++;
            contestEntry.prize = getPositionPrize(contestEntry.position);
            contestEntry.updateRanking();
        }
    }

    public int getPositionPrize(int position) {
        return (position < prizes.size()) ? prizes.get(position) : 0;
    }

    public void givePrizes() {
        if (contestEntries.isEmpty()) {
            return;
        }

        updateRanking();

        // Si el contest tiene premios para repartir...
        if (!prizes.isEmpty()) {
            List<AccountOp> accounts = new ArrayList<>();
            for (ContestEntry contestEntry : contestEntries) {
                if (contestEntry.position < prizes.size()) {
                    User user = User.findOne(contestEntry.userId);
                    accounts.add(new AccountOp(contestEntry.userId, new BigDecimal(prizes.get(contestEntry.position)), user.getSeqId() + 1));
                }
            }
            AccountingTranPrize.create(contestId, accounts);
        }

        ContestEntry winner = null;

        for (ContestEntry contestEntry : contestEntries) {
            if (contestEntry.position == 0) {
                winner = contestEntry;
                break;
            }
        }

        if (winner == null)
            throw new RuntimeException("WTF 7221: givePrizes, winner == null");

        // Actualizamos las estadísticas de torneos ganados
        User userWinner = User.findOne(winner.userId);
        userWinner.updateStats();
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

    public Contest getSameContestWithFreeSlot(ObjectId userId) {
        String query = String.format("{templateContestId: #, 'contestEntries.%s': {$exists: false}, 'contestEntries.userId': {$ne:#}}", maxEntries-1);
        return Model.contests().findOne(query, templateContestId, userId).as(Contest.class);
    }

    class ContestEntryComparable implements Comparator<ContestEntry>{
        @Override
        public int compare(ContestEntry o1, ContestEntry o2) {
            return (o2.fantasyPoints - o1.fantasyPoints);
        }
    }
}
