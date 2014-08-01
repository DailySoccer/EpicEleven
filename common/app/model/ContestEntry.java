package model;

import com.fasterxml.jackson.annotation.JsonView;
import com.mongodb.MongoException;
import org.bson.types.ObjectId;
import org.jongo.marshall.jackson.oid.Id;
import play.Logger;
import utils.ListUtils;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class ContestEntry implements JongoId {
    @Id
    public ObjectId contestEntryId;
    public ObjectId userId;             // Usuario que creo el equipo
    public ObjectId contestId;          // Contest en el que se ha inscrito el usuario

    @JsonView(JsonViews.FullContest.class)
    public List<ObjectId> soccerIds;    // Fantasy team

    @JsonView(JsonViews.NotForClient.class)
    public Date createdAt;

    public ContestEntry() {}

    public ContestEntry(ObjectId userId, ObjectId contestId, List<ObjectId> soccerIds) {
        this.contestEntryId = new ObjectId();
        this.userId = userId;
        this.contestId = contestId;
        this.soccerIds = soccerIds;
        this.createdAt = GlobalDate.getCurrentDate();
    }

    public ObjectId getId() { return contestEntryId; }

    static public ContestEntry findOne(ObjectId contestEntryId) {
        Contest contest = Model.contests().findOne("{'contestEntries._id' : #}", contestEntryId).as(Contest.class);

        ContestEntry contestEntry = null;

        for (ContestEntry entry : contest.contestEntries) {
            if (entry.contestEntryId.equals(contestEntryId)) {
                contestEntry = entry;
                break;
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

    /**
     * Creacion de un contest entry (se añade a la base de datos)
     * @param user      Usuario al que pertenece el equipo
     * @param contestId   Contest al que se apunta
     * @param soccers   Lista de futbolistas con la que se apunta
     * @return Si se ha realizado correctamente su creacion
     */
    public static boolean create(ObjectId user, ObjectId contestId, List<ObjectId> soccers) {

        boolean bRet = false;

        try {
            Contest contest = Model.contests().findOne("{ _id: # }", contestId).as(Contest.class);
            if (contest != null) {
                ContestEntry aContestEntry = new ContestEntry(user, contestId, soccers);
                contest.contestEntries.add(aContestEntry);
                Model.contests().update(contest.contestId).with(contest);

                bRet = true;
            }
        }
        catch (MongoException exc) {
            Logger.error("WTF 2032: ", exc);
        }

        return bRet;
    }
}
