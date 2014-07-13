package model;

import org.bson.types.ObjectId;
import org.jongo.Find;
import org.jongo.marshall.jackson.oid.Id;
import play.Logger;

import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.ArrayList;

public class Contest {

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

    static public Contest findOne(ObjectId contestId) {
        return Model.contests().findOne("{_id : #}", contestId).as(Contest.class);
    }

    static public Contest findOne(String contestId) {
        Contest aContest = null;
        Boolean userValid = ObjectId.isValid(contestId);
        if (userValid) {
            aContest = Model.contests().findOne(new ObjectId(contestId)).as(Contest.class);
        }
        return aContest;
    }

    public static Find findAllFromContestEntries(Iterable<ContestEntry> contestEntries) {
        List<ObjectId> contestIds = new ArrayList<>();

        for (ContestEntry contestEntry : contestEntries) {
            contestIds.add(contestEntry.contestId);
        }

        return Model.findObjectIds(Model.contests(), "_id", contestIds);
    }

    static public Find findAllFromTemplateContests(Iterable<TemplateContest> templateContests) {
        List<ObjectId> templateContestObjectIds = new ArrayList<>();

        for (TemplateContest template: templateContests) {
            templateContestObjectIds.add(template.templateContestId);
        }

        return Model.findObjectIds(Model.contests(), "templateContestId", templateContestObjectIds);
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