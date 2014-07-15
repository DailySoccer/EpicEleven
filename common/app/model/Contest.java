package model;

import org.bson.types.ObjectId;
import org.jongo.Find;
import org.jongo.marshall.jackson.oid.Id;
import play.Logger;
import utils.ListUtils;

import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.ArrayList;

public class Contest implements JongoId {

    @Id
    public ObjectId contestId;
    public ObjectId templateContestId;

    public Date createdAt;
    public String name;

    public List<ObjectId> currentUserIds = new ArrayList<>();
    public int maxEntries;


    public Contest() {}

    public Contest(TemplateContest template) {
        templateContestId = template.templateContestId;
        name = template.name;
        maxEntries = template.maxEntries;
        createdAt = GlobalDate.getCurrentDate();
    }

    public ObjectId getId() { return contestId; }


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

    public static List<Contest> findAllFromContestEntries(Iterable<ContestEntry> contestEntries) {
        List<ObjectId> objectIds = new ArrayList<>();

        for (ContestEntry contestEntry : contestEntries) {
            objectIds.add(contestEntry.contestId);
        }

        return ListUtils.asList(Model.findObjectIds(Model.contests(), "_id", objectIds).as(Contest.class));
    }

    static public List<Contest> findAllFromTemplateContests(Iterable<TemplateContest> templateContests) {
        return ListUtils.asList(Model.findObjectIds(Model.contests(), "templateContestId", ListUtils.convertToIdList(templateContests)).as(Contest.class));
    }

    /**
     * Eliminar un contest y sus dependencias
     */
    public static boolean remove(Contest contest) {
        Logger.info("remove Contest ({}): {}", contest.contestId, contest.name);

        // Eliminar los contest entries de ese contest
        Model.contestEntries().remove("{contestId: #}", contest.contestId);

        // Eliminar el contest
        Model.contests().remove(contest.contestId);

        return true;
    }
}