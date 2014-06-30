package model;

import org.bson.types.ObjectId;
import org.jongo.Find;
import org.jongo.marshall.jackson.oid.Id;
import play.Logger;

import java.util.List;
import java.util.ArrayList;

public class Contest {

    @Id
    public ObjectId contestId;

    public String name;

    public List<ObjectId> currentUserIds = new ArrayList<>();
    public int maxUsers;

    public ObjectId templateContestId;

    // Constructor por defecto (necesario para Jongo: "unmarshall result to class")
    public Contest() {
    }

    public Contest(TemplateContest template) {
        templateContestId = template.templateContestId;
        name = template.name;
    }

    static public Contest find(ObjectId contestId) {
        return Model.contests().findOne("{_id : #}", contestId).as(Contest.class);
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

    /**
     * Query de un contest por su identificador en mongoDB (verifica la validez del mismo)
     *
     * @param contestId Identficador del contest
     * @return Contest
     */
    static public Contest find(String contestId) {
        Contest aContest = null;
        Boolean userValid = ObjectId.isValid(contestId);
        if (userValid) {
            aContest = Model.contests().findOne(new ObjectId(contestId)).as(Contest.class);
        }
        return aContest;
    }

    /**
     *  Query de la lista de Contests correspondientes a una lista de Template Contests
     */
    static public Find find(List<TemplateContest> templateContests) {
        List<ObjectId> templateContestObjectIds = new ArrayList<>(templateContests.size());
        for (TemplateContest template: templateContests) {
            templateContestObjectIds.add(template.templateContestId);
        }
        return Model.findObjectIds(Model.contests(), "templateContestId", templateContestObjectIds);
    }
}
