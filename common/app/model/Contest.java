package model;

import com.fasterxml.jackson.annotation.JsonView;
import com.mongodb.BulkWriteOperation;
import org.bson.types.ObjectId;
import org.jongo.marshall.jackson.oid.Id;
import utils.ListUtils;
import utils.ViewProjection;

import java.util.*;

public class Contest implements JongoId {
    @Id
    public ObjectId contestId;
    public ObjectId templateContestId;

    @JsonView(JsonViews.NotForClient.class)
    public Date createdAt;

    @JsonView(value={JsonViews.Extended.class, JsonViews.AllContests.class})
    public ContestState state = ContestState.OFF;

    public String name;

    @JsonView(value={JsonViews.Public.class, JsonViews.AllContests.class})
    public List<ContestEntry> contestEntries = new ArrayList<>();

    @JsonView(JsonViews.Public.class)
    public int getNumEntries() {
        return contestEntries.size();
    }

    public int maxEntries;

    public int salaryCap;

    @JsonView(value={JsonViews.Public.class, JsonViews.AllContests.class})
    public int entryFee;
    @JsonView(value={JsonViews.Public.class, JsonViews.AllContests.class})
    public PrizeType prizeType;

    @JsonView(JsonViews.Extended.class)
    public List<Integer> prizes;

    public Date startDate;

    @JsonView(value={JsonViews.Extended.class, JsonViews.MyLiveContests.class})
    public List<ObjectId> templateMatchEventIds = new ArrayList<>();

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
        templateMatchEventIds = template.templateMatchEventIds;
        createdAt = GlobalDate.getCurrentDate();
    }

    public ObjectId getId() { return contestId; }

    public boolean isOff()      { return (state == ContestState.OFF); }
    public boolean isActive()   { return (state == ContestState.ACTIVE); }
    public boolean isLive()     { return (state == ContestState.LIVE); }
    public boolean isHistory()  { return (state == ContestState.HISTORY); }

    public boolean isFull() { return getNumEntries() >= maxEntries; }

    public List<MatchEvent> getMatchEvents() {
        return MatchEvent.findAllFromTemplates(templateMatchEventIds);
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

    static public Contest findOneFromContestEntry(ObjectId contestEntryId) {
        return Model.contests().findOne("{'contestEntries._id' : #}", contestEntryId).as(Contest.class);
    }

    public static List<Contest> findAllFromUser(ObjectId userId) {
        return ListUtils.asList(Model.contests().find("{'contestEntries.userId': #}", userId).as(Contest.class));
    }

    static public List<Contest> findAllFromTemplateContest(ObjectId templateContestId) {
        return ListUtils.asList(Model.contests().find("{templateContestId: #}", templateContestId).as(Contest.class));
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

    public void updateRanking(BulkWriteOperation bulkOperation, TemplateContest templateContest, List<MatchEvent> matchEvents) {
        // Actualizamos los fantasy points
        for (ContestEntry contestEntry : contestEntries) {
            contestEntry.fantasyPoints = contestEntry.getFantasyPointsFromMatchEvents(matchEvents);
        }
        // Los ordenamos según los fantasy points
        Collections.sort (contestEntries, new ContestEntryComparable());
        // Actualizamos sus "posiciones"
        int index = 0;
        for (ContestEntry contestEntry : contestEntries) {
            contestEntry.position = index++;
            contestEntry.prize = templateContest.getPositionPrize(contestEntry.position);
            // contestEntry.updateRanking();
            contestEntry.updateRanking(bulkOperation);
        }
    }

    public void givePrizes() {
        ContestEntry winner = null;

        for (ContestEntry contestEntry : contestEntries) {
            if (contestEntry.position == 0) {
                winner = contestEntry;
                break;
            }
        }

        if (winner == null)
            throw new RuntimeException("WTF 7221: givePrizes, winner == null");

        User user = User.findOne(winner.userId);

        // TODO: Dar premios
        // Actualmente únicamente actualizamos las estadísticas de torneos ganados
        user.updateStats();
    }

    public Contest getSameContestWithFreeSlot() {
        String query = String.format("{templateContestId: #, 'contestEntries.%s': {$exists: false}}", maxEntries-1);
        return Model.contests().findOne(query, templateContestId).as(Contest.class);
    }

    class ContestEntryComparable implements Comparator<ContestEntry>{
        @Override
        public int compare(ContestEntry o1, ContestEntry o2) {
            return (o2.fantasyPoints - o1.fantasyPoints);
        }
    }
}
