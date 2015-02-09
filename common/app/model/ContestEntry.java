package model;

import com.fasterxml.jackson.annotation.JsonView;
import com.mongodb.MongoException;
import com.mongodb.WriteResult;
import org.bson.types.ObjectId;
import org.joda.money.CurrencyUnit;
import org.joda.money.Money;
import org.jongo.marshall.jackson.oid.Id;
import play.Logger;
import utils.ListUtils;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class ContestEntry implements JongoId {
    @Id
    public ObjectId contestEntryId;

    @JsonView(value={JsonViews.Public.class, JsonViews.AllContests.class})
    public ObjectId userId;             // Usuario que creo el equipo

    @JsonView(value={JsonViews.FullContest.class, JsonViews.MyLiveContests.class})
    public List<ObjectId> soccerIds;    // Fantasy team

    @JsonView(value={JsonViews.Extended.class, JsonViews.MyHistoryContests.class})
    public int position = -1;

    @JsonView(value={JsonViews.Extended.class, JsonViews.MyHistoryContests.class})
    public Money prize = Money.zero(CurrencyUnit.EUR);

    @JsonView(value={JsonViews.Extended.class, JsonViews.MyHistoryContests.class})
    public int fantasyPoints;

    @JsonView(JsonViews.NotForClient.class)
    public Date createdAt;

    public ContestEntry() {}

    public ContestEntry(ObjectId userId, List<ObjectId> soccerIds) {
        this.contestEntryId = new ObjectId();
        this.userId = userId;
        this.soccerIds = soccerIds;
        this.createdAt = GlobalDate.getCurrentDate();
    }

    public ObjectId getId() { return contestEntryId; }

    public void updateRanking() {
        // Logger.info("ContestEntry: {} | UserId: {} | Position: {} | FantasyPoints: {}", contestEntryId, userId, position, fantasyPoints);

        Model.contests()
            .update("{'contestEntries._id': #}", getId())
            .with("{$set: {'contestEntries.$.position': #, 'contestEntries.$.fantasyPoints': #, 'contestEntries.$.prize': #}}",
                    position, fantasyPoints, prize.toString());
    }


    static public ContestEntry findOne(String contestEntryId) {
        ContestEntry aContestEntry = null;
        if (ObjectId.isValid(contestEntryId)) {
            aContestEntry = findOne(new ObjectId(contestEntryId));
        }
        return aContestEntry;
    }

    static public ContestEntry findOne(ObjectId contestEntryId) {
        ContestEntry contestEntry = null;

        Contest contest = Contest.findOneFromContestEntry(contestEntryId);
        if (contest != null) {
            for (ContestEntry entry : contest.contestEntries) {
                if (entry.contestEntryId.equals(contestEntryId)) {
                    contestEntry = entry;
                    break;
                }
            }
        }

        return contestEntry;
    }

    static public List<ContestEntry> findAllFromContests() {
        List<ContestEntry> contestEntries = new ArrayList<>();

        List<Contest> contests = ListUtils.asList(Model.contests().find().as(Contest.class));
        for (Contest contest : contests) {
            contestEntries.addAll(contest.contestEntries);
        }

        return contestEntries;
    }

    public static boolean update(ObjectId userId, ObjectId contestId, ObjectId contestEntryId, List<ObjectId> soccersIds) {

        boolean bRet = false;

        try {
            WriteResult result = Model.contests()
                    .update("{_id: #, state: \"ACTIVE\", contestEntries._id: #, contestEntries.userId: #}", contestId, contestEntryId, userId)
                    .with("{$set: {contestEntries.$.soccerIds: #}}", soccersIds);

            // Comprobamos el número de documentos afectados (error == 0)
            bRet = (result.getN() > 0);
        }
        catch (MongoException exc) {
            Logger.error("WTF 3032: ", exc);
        }

        return bRet;
    }

    public static boolean remove(ObjectId userId, ObjectId contestId, ObjectId contestEntryId) {

        boolean bRet = false;

        try {
            Contest contest = Model.contests()
                    .findAndModify("{_id: #, state: \"ACTIVE\", contestEntries._id: #, contestEntries.userId: #}", contestId, contestEntryId, userId)
                    .with("{$pull: {contestEntries: {_id: #}}}", contestEntryId)
                    .as(Contest.class);

            if (contest != null) {
                // Registrar el contestEntry eliminado
                ContestEntry cancelledContestEntry = contest.findContestEntry(contestEntryId);
                Model.cancelledContestEntries().insert(cancelledContestEntry);
                bRet = true;
            }
        }
        catch (MongoException exc) {
            Logger.error("WTF 7801: ", exc);
        }

        return bRet;
    }

    public int getFantasyPointsFromMatchEvents(List<TemplateMatchEvent> templateMatchEvents) {
        int fantasyPoints = 0;
        for (ObjectId templateSoccerPlayerId : soccerIds) {
            for (TemplateMatchEvent templateMatchEvent : templateMatchEvents) {
                if (templateMatchEvent.containsTemplateSoccerPlayer(templateSoccerPlayerId)) {
                    fantasyPoints += templateMatchEvent.getSoccerPlayerFantasyPoints(templateSoccerPlayerId);
                    break;
                }
            }
        }
        return fantasyPoints;
    }
}
